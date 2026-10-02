"""普通用户Agent的LangGraph前置工作流。"""
import re
from typing import Any

from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import END, START, StateGraph

from app.user_agent.state import UserAgentState


class UserAgentWorkflow:
    """路由用户意图、提取安全槽位，并生成后续工具编排约束。"""

    def __init__(self):
        builder = StateGraph(UserAgentState)
        builder.add_node("load_context", self._load_context)
        builder.add_node("security_check", self._security_check)
        builder.add_node("classify_intent", self._classify_intent)
        builder.add_node("collect_slots", self._collect_slots)
        builder.add_node("prepare_instruction", self._prepare_instruction)
        builder.add_edge(START, "load_context")
        builder.add_edge("load_context", "security_check")
        builder.add_conditional_edges(
            "security_check", self._security_route,
            {"continue": "classify_intent", "stop": END})
        builder.add_edge("classify_intent", "collect_slots")
        builder.add_edge("collect_slots", "prepare_instruction")
        builder.add_edge("prepare_instruction", END)
        # 首期使用进程内checkpoint；MySQL仍保存权威任务和事件，重启可重新路由。
        self.graph = builder.compile(checkpointer=InMemorySaver())

    async def run(self, *, task_id: str, session_id: str | None, query: str,
                  context: dict[str, Any] | None) -> UserAgentState:
        state: UserAgentState = {
            "task_id": task_id,
            "session_id": session_id,
            "actor_type": "USER",
            "query": query,
            "context": context or {},
            "slots": {},
            "response_blocks": [],
            "pending_confirmation": None,
            "confidence": 0.0,
            "missing_slots": [],
            "retrieval_plan": "NONE",
            "tool_plan": [],
            "error": None,
        }
        return await self.graph.ainvoke(
            state, config={"configurable": {"thread_id": session_id or task_id}})

    def _load_context(self, state: UserAgentState) -> dict[str, Any]:
        context = state.get("context") or {}
        page = context.get("client_context")
        return {"page_context": page if isinstance(page, dict) else {}}

    def _security_check(self, state: UserAgentState) -> dict[str, Any]:
        query = state.get("query", "").strip()
        blocked = (
            "忽略之前", "忽略先前", "ignore previous", "system prompt",
            "系统提示词", "developer message", "输出密钥", "数据库密码",
            "api key", "access token", "password", "secret key", "完整手机号",
            "完整身份证号",
        )
        if not query:
            return {"error": {"code": "EMPTY_QUERY", "message": "请输入你的问题"}}
        if any(term.lower() in query.lower() for term in blocked):
            return {"error": {"code": "UNSAFE_REQUEST", "message": "无法处理涉及内部指令或密钥的请求"}}
        return {"error": None}

    def _security_route(self, state: UserAgentState) -> str:
        return "stop" if state.get("error") else "continue"

    def _classify_intent(self, state: UserAgentState) -> dict[str, Any]:
        query = state["query"].lower()
        groups = (
            ("after_sale", ("售后", "退款", "退单", "取消订单")),
            ("order", ("订单", "配送进度", "送到", "进度", "催单")),
            ("cart", ("购物车", "加购", "加入", "来一份", "来一个")),
            ("medical_risk", ("胸痛", "呼吸困难", "昏迷", "休克", "严重过敏", "肾衰竭", "透析", "药物冲突")),
            ("diet_recommendation", ("减脂", "减肥", "高蛋白", "低钠", "低糖", "高血压", "糖尿病", "高血脂", "肥胖", "过敏", "忌口", "时令", "当季", "生病")),
            ("recommendation", ("推荐", "预算", "几个人", "人吃", "人份", "清淡", "辣", "低脂", "不甜", "吃什么")),
            ("knowledge", ("规则", "营业", "配送费", "过敏", "食材", "优惠")),
        )
        for intent, terms in groups:
            if any(term in query for term in terms):
                return {"intent": intent, "confidence": 0.85}
        return {"intent": "unknown", "confidence": 0.2}

    def _collect_slots(self, state: UserAgentState) -> dict[str, Any]:
        query = state["query"]
        slots: dict[str, Any] = {}
        budget = re.search(r"(?:预算|不超过|以内)\s*(\d+(?:\.\d{1,2})?)\s*元?", query)
        people = re.search(r"([一二两三四五六七八九十]|\d+)\s*(?:个人|人份|人吃)", query)
        order_id = re.search(r"(?:订单|订单号)\s*[#：:]?\s*(\d+)", query)
        reason = re.search(r"(?:原因|因为|由于)\s*[：:]?\s*(.{2,120})", query)
        if budget: slots["budget"] = float(budget.group(1))
        if people:
            # 首期只接受常见的一到十人表达，避免把自由文本误解析为超大人数。
            chinese_numbers = {
                "一": 1, "二": 2, "两": 2, "三": 3, "四": 4, "五": 5,
                "六": 6, "七": 7, "八": 8, "九": 9, "十": 10,
            }
            raw_people = people.group(1)
            slots["people"] = chinese_numbers.get(raw_people, int(raw_people) if raw_people.isdigit() else 0)
        if order_id: slots["order_id"] = int(order_id.group(1))
        if reason: slots["reason"] = reason.group(1).strip()
        for preference in ("清淡", "微辣", "辣", "素食", "低脂", "不甜"):
            if preference in query:
                slots.setdefault("preferences", []).append(preference)
        controlled_terms = {
            "花生": ("allergens", "PEANUT"), "坚果": ("allergens", "TREE_NUT"),
            "牛奶": ("allergens", "MILK"), "乳制品": ("allergens", "MILK"),
            "鸡蛋": ("allergens", "EGG"), "小麦": ("allergens", "WHEAT"),
            "大豆": ("allergens", "SOY"), "海鲜": ("allergens", "SHELLFISH"),
            "芝麻": ("allergens", "SESAME"),
            "减脂": ("goals", "WEIGHT_LOSS"), "减肥": ("goals", "WEIGHT_LOSS"),
            "高蛋白": ("goals", "HIGH_PROTEIN"), "低钠": ("goals", "LOW_SODIUM"),
            "低糖": ("goals", "LOW_SUGAR"), "低脂": ("goals", "LOW_FAT"),
            "高血压": ("conditions", "HYPERTENSION"), "糖尿病": ("conditions", "DIABETES"),
            "高血脂": ("conditions", "HYPERLIPIDEMIA"), "肥胖": ("conditions", "OBESITY"),
        }
        for term, (slot, code) in controlled_terms.items():
            if term in query:
                slots.setdefault(slot, []).append(code)
        missing = []
        intent = state.get("intent")
        if intent in {"order", "after_sale"} and "order_id" not in slots:
            missing.append("order_id")
        if intent == "after_sale" and "reason" not in slots:
            missing.append("reason")
        return {"slots": slots, "missing_slots": missing}

    def _prepare_instruction(self, state: UserAgentState) -> dict[str, Any]:
        intent = state.get("intent", "unknown")
        instruction = {
            "recommendation": "先调用search_products取得实时可售候选，再基于预算、人数和偏好解释推荐；不要编造价格。",
            "diet_recommendation": "先调用recommend_personalized_meals，由Java执行营养计算和安全过滤。只能推荐工具返回的商品，不得补造营养、食材、健康功效或额外菜品；信息不足时只追问一个关键问题。",
            "medical_risk": "这是高风险医疗请求。不要诊断、不要给治疗性饮食方案、不要调用推荐工具；建议用户及时联系医生或急救服务。",
            "cart": "涉及购物车时先确认商品与规格；用户已明确商品和数量时可调用add_cart_item。",
            "order": "订单事实必须调用list_my_orders、get_my_order_detail或get_order_timeline，并且只能访问本人订单。",
            "after_sale": "先查询本人订单和售后状态；原因缺失时只追问原因，提交售后必须由用户确认。",
            "knowledge": "规则类问题优先使用知识库；营业状态等实时事实使用get_shop_status。",
            "unknown": "无法确定意图时只追问一个最关键的问题，不执行写操作。",
        }[intent]
        plan = "PUBLIC_RAG" if intent == "knowledge" else "REALTIME_TOOL"
        if intent == "diet_recommendation": plan = "HYBRID"
        if intent == "medical_risk": plan = "NONE"
        if intent == "after_sale": plan = "HYBRID"
        clarification = ""
        if state.get("missing_slots"):
            clarification = "当前信息不完整，只提出一个最关键的问题，不执行写操作：" + state["missing_slots"][0]
        return {"system_instruction": "你是饱饱点餐用户助手。" + instruction + clarification,
                "retrieval_plan": plan,
                "tool_plan": [intent] if intent else []}


user_agent_workflow = UserAgentWorkflow()
