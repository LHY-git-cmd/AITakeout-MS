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
            ("recommendation", ("推荐", "预算", "几个人", "人吃", "人份", "清淡", "辣", "低脂", "不甜", "忌口", "吃什么")),
            ("knowledge", ("规则", "营业", "配送费", "过敏", "食材", "优惠")),
        )
        for intent, terms in groups:
            if any(term in query for term in terms):
                return {"intent": intent}
        return {"intent": "unknown"}

    def _collect_slots(self, state: UserAgentState) -> dict[str, Any]:
        query = state["query"]
        slots: dict[str, Any] = {}
        budget = re.search(r"(?:预算|不超过|以内)\s*(\d+(?:\.\d{1,2})?)\s*元?", query)
        people = re.search(r"([一二两三四五六七八九十]|\d+)\s*(?:个人|人份|人吃)", query)
        order_id = re.search(r"(?:订单|订单号)\s*[#：:]?\s*(\d+)", query)
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
        for preference in ("清淡", "微辣", "辣", "素食", "低脂", "不甜"):
            if preference in query:
                slots.setdefault("preferences", []).append(preference)
        return {"slots": slots}

    def _prepare_instruction(self, state: UserAgentState) -> dict[str, Any]:
        intent = state.get("intent", "unknown")
        instruction = {
            "recommendation": "先调用search_products取得实时可售候选，再基于预算、人数和偏好解释推荐；不要编造价格。",
            "cart": "涉及购物车时先确认商品与规格；用户已明确商品和数量时可调用add_cart_item。",
            "order": "订单事实必须调用list_my_orders、get_my_order_detail或get_order_timeline，并且只能访问本人订单。",
            "after_sale": "先查询本人订单和售后状态；MVP不创建售后申请，需明确告知用户从订单详情页提交。",
            "knowledge": "规则类问题优先使用知识库；营业状态等实时事实使用get_shop_status。",
            "unknown": "无法确定意图时只追问一个最关键的问题，不执行写操作。",
        }[intent]
        return {"system_instruction": "你是饱饱点餐用户助手。" + instruction}


user_agent_workflow = UserAgentWorkflow()
