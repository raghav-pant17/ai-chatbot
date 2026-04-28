import sys
import textwrap
from pathlib import Path


PAGE_WIDTH = 595
PAGE_HEIGHT = 842
LEFT_MARGIN = 42
TOP_MARGIN = 800
BOTTOM_MARGIN = 42
FONT_SIZE = 9
LEADING = 12
MAX_CHARS = 96


def escape_pdf_text(value: str) -> str:
    return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


def prepare_lines(markdown: str) -> list[str]:
    lines: list[str] = []
    for raw_line in markdown.splitlines():
        line = raw_line.rstrip()
        if not line:
            lines.append("")
            continue
        if line.startswith("# "):
            lines.append(line[2:].upper())
            lines.append("=" * min(len(line[2:]), MAX_CHARS))
            continue
        if line.startswith("## "):
            lines.append("")
            lines.append(line[3:].upper())
            lines.append("-" * min(len(line[3:]), MAX_CHARS))
            continue
        if line.startswith("### "):
            lines.append("")
            lines.append(line[4:])
            continue
        if line.startswith("|") or line.startswith("```"):
            lines.append(line[:MAX_CHARS])
            continue
        if line.startswith("- ") or line.startswith("1. ") or line.startswith("2. ") or line.startswith("3. "):
            wrapped = textwrap.wrap(line, MAX_CHARS, subsequent_indent="  ")
        else:
            wrapped = textwrap.wrap(line, MAX_CHARS)
        lines.extend(wrapped or [""])
    return lines


def paginate(lines: list[str]) -> list[list[str]]:
    lines_per_page = int((TOP_MARGIN - BOTTOM_MARGIN) / LEADING)
    pages: list[list[str]] = []
    page: list[str] = []
    for line in lines:
        if len(page) >= lines_per_page:
            pages.append(page)
            page = []
        page.append(line)
    if page:
        pages.append(page)
    return pages


def page_stream(lines: list[str], page_number: int) -> bytes:
    commands = [
        "BT",
        f"/F1 {FONT_SIZE} Tf",
        f"{LEADING} TL",
        f"{LEFT_MARGIN} {TOP_MARGIN} Td",
    ]
    for line in lines:
        commands.append(f"({escape_pdf_text(line)}) Tj")
        commands.append("T*")
    commands.append(f"({escape_pdf_text('Page ' + str(page_number))}) Tj")
    commands.append("ET")
    return ("\n".join(commands)).encode("latin-1", errors="replace")


def build_pdf(pages: list[list[str]]) -> bytes:
    objects: list[bytes] = []

    def add_object(data: bytes) -> int:
        objects.append(data)
        return len(objects)

    catalog_id = add_object(b"<< /Type /Catalog /Pages 2 0 R >>")
    pages_id = add_object(b"")
    font_id = add_object(b"<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>")

    page_ids: list[int] = []
    content_ids: list[int] = []
    for index, page_lines in enumerate(pages, start=1):
        stream = page_stream(page_lines, index)
        content_id = add_object(b"<< /Length " + str(len(stream)).encode("ascii") + b" >>\nstream\n" + stream + b"\nendstream")
        page_id = add_object(
            f"<< /Type /Page /Parent {pages_id} 0 R /MediaBox [0 0 {PAGE_WIDTH} {PAGE_HEIGHT}] "
            f"/Resources << /Font << /F1 {font_id} 0 R >> >> /Contents {content_id} 0 R >>".encode("ascii")
        )
        content_ids.append(content_id)
        page_ids.append(page_id)

    kids = " ".join(f"{page_id} 0 R" for page_id in page_ids)
    objects[pages_id - 1] = f"<< /Type /Pages /Kids [{kids}] /Count {len(page_ids)} >>".encode("ascii")

    output = bytearray(b"%PDF-1.4\n")
    offsets = [0]
    for obj_id, data in enumerate(objects, start=1):
        offsets.append(len(output))
        output.extend(f"{obj_id} 0 obj\n".encode("ascii"))
        output.extend(data)
        output.extend(b"\nendobj\n")

    xref_offset = len(output)
    output.extend(f"xref\n0 {len(objects) + 1}\n".encode("ascii"))
    output.extend(b"0000000000 65535 f \n")
    for offset in offsets[1:]:
        output.extend(f"{offset:010d} 00000 n \n".encode("ascii"))
    output.extend(
        f"trailer\n<< /Size {len(objects) + 1} /Root {catalog_id} 0 R >>\n"
        f"startxref\n{xref_offset}\n%%EOF\n".encode("ascii")
    )
    return bytes(output)


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: python markdown_to_simple_pdf.py input.md output.pdf")

    source = Path(sys.argv[1])
    target = Path(sys.argv[2])
    markdown = source.read_text(encoding="utf-8")
    lines = prepare_lines(markdown)
    pages = paginate(lines)
    target.write_bytes(build_pdf(pages))


if __name__ == "__main__":
    main()
