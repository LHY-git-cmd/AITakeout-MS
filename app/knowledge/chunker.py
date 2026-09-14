import hashlib
import uuid


class StructureChunker:
    """
    结构化文本分块器。
    将长文本按指定的目标字符数分割成更小的、有重叠的块。
    """
    def __init__(self, target_chars=400, overlap_chars=60):
        """
        初始化分块器。

        :param target_chars: 每个块的目标字符数。
        :param overlap_chars: 块之间的重叠字符数，以保证上下文连续性。
        """
        self.target_chars = target_chars
        self.overlap_chars = overlap_chars

    def split(self, sections: list[dict], metadata: dict) -> list[dict]:
        """
        将一系列文本节（sections）分割成块（chunks）。

        :param sections: 从文档解析出的节列表，每个节包含 "text" 字段。
        :param metadata: 应用于所有块的元数据字典 (例如 kb_id, document_id)。
        :return: 一个包含所有生成块的列表。
        """
        chunks = []
        for section in sections:
            text = section["text"].strip()
            start = 0
            while text and start < len(text):
                # 确定块的结束位置
                end = min(len(text), start + self.target_chars)
                if end < len(text):
                    # 尽量在句子边界（如换行符或句号）分割，以保持语义完整性
                    boundary = max(text.rfind("\n", start, end), text.rfind("。", start, end))
                    if boundary > start + self.target_chars // 2:
                        end = boundary + 1
                
                content = text[start:end].strip()
                if content:
                    # 为每个块生成唯一的 ID 和内容哈希
                    digest = hashlib.sha256(content.encode("utf-8")).hexdigest()
                    chunk_id = str(uuid.uuid5(uuid.NAMESPACE_URL,
                        f"{metadata['document_id']}:{metadata['document_version']}:{digest}"))
                    
                    # 将元数据和块内容合并
                    chunks.append({
                        **metadata, 
                        **section, 
                        "content": content,
                        "content_hash": digest, 
                        "chunk_id": chunk_id, 
                        "enabled": True
                    })
                
                if end >= len(text):
                    break
                
                # 计算下一个块的起始位置，实现重叠
                start = max(start + 1, end - self.overlap_chars)
        return chunks
