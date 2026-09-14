import re
from pathlib import Path


class DocumentParser:
    """
    文档解析器。
    支持解析 PDF, DOCX, Markdown, 和 TXT 文件，将它们分割成带有结构信息（如标题路径）的文本块。
    """

    def parse(self, path: str, file_type: str) -> list[dict]:
        """
        根据文件类型调用相应的解析方法。

        :param path: 文件路径。
        :param file_type: 文件类型 (例如 "pdf", "docx", "md", "txt")。
        :return: 一个包含文本节（sections）的字典列表。
        :raises ValueError: 如果文件类型不受支持。
        """
        kind = file_type.lower().lstrip(".")
        if kind == "pdf":
            return self._pdf(path)
        if kind == "docx":
            return self._docx(path)
        if kind in {"md", "markdown"}:
            return self._markdown(path)
        if kind == "txt":
            return [{"text": self._read_text(path), "page_no": None, "title_path": []}]
        raise ValueError(f"unsupported file type: {file_type}")

    def _pdf(self, path):
        """
        解析 PDF 文件，按页提取文本。

        :param path: PDF 文件的路径。
        :return: 包含每页内容的节列表。
        """
        from pypdf import PdfReader

        return [
            {
                "text": page.extract_text() or "",
                "page_no": index + 1,
                "title_path": [],
            }
            for index, page in enumerate(PdfReader(path).pages)
        ]

    def _docx(self, path):
        """
        解析 DOCX 文件，根据标题结构（Heading styles）分割文本。

        :param path: DOCX 文件的路径。
        :return: 包含结构化文本块的节列表。
        """
        from docx import Document

        sections, titles, buffer = [], [], []
        for paragraph in Document(path).paragraphs:
            text = self._clean(paragraph.text)
            if not text:
                continue
            if paragraph.style and paragraph.style.name.lower().startswith("heading"):
                if buffer:
                    sections.append(
                        {
                            "text": "\n".join(buffer),
                            "page_no": None,
                            "title_path": titles[:],
                        }
                    )
                    buffer = []
                level = int(re.sub(r"\D", "", paragraph.style.name) or "1")
                titles = titles[:level - 1] + [text]
            else:
                buffer.append(text)
        if buffer:
            sections.append(
                {
                    "text": "\n".join(buffer),
                    "page_no": None,
                    "title_path": titles[:],
                }
            )
        return sections

    def _markdown(self, path):
        """
        解析 Markdown 文件，根据标题（#）分割文本。

        :param path: Markdown 文件的路径。
        :return: 包含结构化文本块的节列表。
        """
        sections, titles, buffer = [], [], []
        for line in self._read_text(path).splitlines():
            match = re.match(r"^(#{1,6})\s+(.+)$", line)
            if match:
                if buffer:
                    sections.append(
                        {
                            "text": "\n".join(buffer),
                            "page_no": None,
                            "title_path": titles[:],
                        }
                    )
                    buffer = []
                level = len(match.group(1))
                titles = titles[:level - 1] + [match.group(2).strip()]
            else:
                buffer.append(line)
        if buffer:
            sections.append(
                {
                    "text": "\n".join(buffer),
                    "page_no": None,
                    "title_path": titles[:],
                }
            )
        return sections

    def _read_text(self, path):
        """
        读取文本文件，自动检测编码（UTF-8, GB18030）并清理内容。

        :param path: 文本文件的路径。
        :return: 清理后的文本内容。
        :raises ValueError: 如果无法解码文件。
        """
        raw = Path(path).read_bytes()
        for encoding in ("utf-8-sig", "utf-8", "gb18030"):
            try:
                return self._clean(raw.decode(encoding))
            except UnicodeDecodeError:
                continue
        raise ValueError("unable to decode text document")

    @staticmethod
    def _clean(text):
        """
        清理文本，移除控制字符、多余的空格和换行符。

        :param text: 原始文本。
        :return: 清理后的文本。
        """
        text = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", "", text)
        text = re.sub(r"[ \t]+", " ", text)
        return re.sub(r"\n{3,}", "\n\n", text).strip()