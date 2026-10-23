"""Read the supplied workbook without changing it and produce UI-only fixture data."""

import json
from pathlib import Path

from openpyxl import load_workbook


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent / "工艺文件模板" / "DISPEN9243-40_工艺文件导入模板.xlsx"
TARGET = ROOT / "src" / "data" / "process.json"

sheet = load_workbook(SOURCE, read_only=True, data_only=True)["工艺步骤"]
rows = list(sheet.values)
steps = []
for excel_row, row in enumerate(rows[2:], start=3):
    if not row[2]:
        continue
    params = []
    for index, (type_col, name_col, value_col) in enumerate(((5, 6, 7), (8, 9, 10), (11, 12, 13)), start=1):
        raw_type, name, reference = row[type_col], row[name_col], row[value_col]
        if not name and reference in (None, ""):
            continue
        is_select = str(raw_type).strip() in ("选项", "单选", "1")
        reference = "" if reference is None else str(reference).strip()
        params.append(
            {
                "index": index,
                "name": str(name or "").strip(),
                "type": 1 if is_select else 0,
                "referenceValue": reference,
                "options": [v.strip() for v in reference.splitlines() if v.strip()] if is_select else [],
                "sourceTypeMissing": raw_type in (None, ""),
            }
        )
    steps.append(
        {
            "stepNo": int(row[2]),
            "title": str(row[1] or "").strip(),
            "importance": int(row[3] or 0),
            "videoName": str(row[4] or "NONE").strip(),
            "parameters": params,
            "sourceRow": excel_row,
        }
    )

payload = {
    "model": str(rows[2][0]),
    "sourceDocument": str(rows[0][1]),
    "sourceWorkbook": SOURCE.name,
    "steps": steps,
}
TARGET.parent.mkdir(parents=True, exist_ok=True)
TARGET.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"Generated {len(steps)} workbook-derived steps; {sum(p['sourceTypeMissing'] for s in steps for p in s['parameters'])} parameter types missing in source.")
