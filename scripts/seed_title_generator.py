#!/usr/bin/env python3
"""Natural Vietnamese title and description generator for seeded listings."""

from __future__ import annotations

import hashlib
import re


TITLE_DATA: dict[str, dict[str, list[str]]] = {
    "bed": {
        "nouns": ["Giường ngủ", "Giường gỗ", "Khung giường", "Giường đơn", "Giường đôi"],
        "specs": ["1m4", "1m6", "1m8", "gỗ chắc", "kèm nệm mỏng"],
        "conditions": ["còn chắc chắn", "ít trầy xước", "đã vệ sinh sạch", "còn dùng tốt", "không lung lay"],
        "contexts": ["phòng trọ", "căn hộ", "phòng ngủ nhỏ", "gia đình", "góc decor phòng"],
        "intents": ["cần thanh lý", "nhượng lại do chuyển nhà", "bán vì đổi nội thất", "không còn nhu cầu dùng"],
    },
    "cabinet": {
        "nouns": ["Tủ đựng đồ", "Tủ gỗ", "Tủ kệ phòng khách", "Kệ tủ đa năng", "Tủ quần áo nhỏ"],
        "specs": ["2 cánh", "3 ngăn", "nhiều ngăn", "gỗ ép dày", "có kệ phụ"],
        "conditions": ["còn chắc", "cánh tủ đóng mở tốt", "bề mặt còn đẹp", "ít vết xước", "sử dụng ổn định"],
        "contexts": ["phòng ngủ", "phòng khách", "khu bếp", "nhà trọ", "căn hộ nhỏ"],
        "intents": ["thanh lý nhanh", "đổi sang mẫu lớn hơn", "nhượng lại giá tốt", "bán do sắp xếp lại nhà"],
    },
    "chair": {
        "nouns": ["Ghế gỗ", "Ghế ăn", "Ghế ngồi", "Ghế làm việc", "Ghế đơn"],
        "specs": ["lưng tựa", "chân sắt", "mặt đệm", "gỗ tự nhiên", "kiểu gọn"],
        "conditions": ["ngồi chắc", "còn đẹp", "ít dùng", "không lung lay", "đã lau sạch"],
        "contexts": ["bàn ăn", "phòng làm việc", "quán nhỏ", "phòng trọ", "bàn học"],
        "intents": ["thanh lý bớt đồ", "đổi bộ bàn ghế mới", "cần bán gọn", "nhượng lại giá mềm"],
    },
    "table": {
        "nouns": ["Bàn gỗ", "Bàn ăn", "Bàn cafe", "Bàn phòng khách", "Bàn nhỏ"],
        "specs": ["mặt rộng", "chân chắc", "gỗ màu sáng", "gấp gọn", "mặt kính"],
        "conditions": ["còn chắc chắn", "bề mặt còn ổn", "ít xước", "đã vệ sinh", "dùng tốt"],
        "contexts": ["gia đình", "căn hộ", "phòng trọ", "bàn ăn", "tiếp khách"],
        "intents": ["cần thanh lý", "đổi bàn mới", "không còn nhu cầu", "bán do chuyển nhà"],
    },
    "desk": {
        "nouns": ["Bàn làm việc", "Bàn học", "Bàn máy tính", "Bàn gỗ nhỏ", "Bàn workspace"],
        "specs": ["có hộc kéo", "mặt rộng", "chân sắt", "gỗ màu sáng", "kiểu tối giản"],
        "conditions": ["còn cứng cáp", "mặt bàn còn đẹp", "ít trầy", "dùng ổn định", "đã lau sạch"],
        "contexts": ["làm việc tại nhà", "sinh viên", "phòng nhỏ", "để máy tính", "học tập"],
        "intents": ["đổi bàn lớn hơn", "thanh lý vì chuyển nhà", "cần bán nhanh", "nhượng lại giá tốt"],
    },
    "sofa": {
        "nouns": ["Sofa phòng khách", "Ghế sofa", "Sofa băng", "Sofa nhỏ", "Sofa vải"],
        "specs": ["2 chỗ", "3 chỗ", "màu trung tính", "nệm dày", "kích thước gọn"],
        "conditions": ["ngồi êm", "khung còn chắc", "vải còn ổn", "ít xước", "đã hút bụi sạch"],
        "contexts": ["căn hộ", "phòng khách", "studio", "nhà nhỏ", "tiếp khách"],
        "intents": ["đổi sofa mới", "thanh lý do chuyển nhà", "nhượng lại nhanh", "bán vì đổi layout"],
    },
    "blender": {
        "nouns": ["Máy xay sinh tố", "Máy xay đa năng", "Máy xay mini", "Máy xay nhà bếp", "Bộ máy xay"],
        "specs": ["có cối xay", "lưỡi xay còn tốt", "còn bình", "còn nắp", "còn dây nguồn"],
        "conditions": ["chạy ổn", "xay khỏe", "ít dùng", "đã vệ sinh", "còn dùng tốt"],
        "contexts": ["bếp gia đình", "làm sinh tố", "xay đồ ăn dặm", "phòng trọ", "dùng hằng ngày"],
        "intents": ["thanh lý vì ít dùng", "đổi máy mới", "nhượng lại giá tốt", "cần dọn bếp"],
    },
    "dishwasher": {
        "nouns": ["Máy rửa chén", "Máy rửa bát", "Máy rửa chén gia đình", "Máy rửa chén mini", "Máy rửa chén độc lập"],
        "specs": ["sức rửa ổn", "tiết kiệm nước", "khoang máy sạch", "có chế độ sấy", "kích thước gọn"],
        "conditions": ["hoạt động bình thường", "rửa sạch", "ít dùng", "còn ổn định", "đã vệ sinh"],
        "contexts": ["căn hộ", "bếp gia đình", "nhà nhỏ", "giảm việc rửa bát", "dùng hằng ngày"],
        "intents": ["đổi sang máy lớn hơn", "thanh lý do chuyển nhà", "nhượng lại giá tốt", "không còn nhu cầu"],
    },
    "fan": {
        "nouns": ["Quạt điện", "Quạt đứng", "Quạt bàn", "Quạt treo tường", "Quạt máy"],
        "specs": ["3 tốc độ", "lồng quạt còn chắc", "chạy êm", "gió mạnh", "có đảo chiều"],
        "conditions": ["còn mát", "chạy ổn", "ít ồn", "đã lau sạch", "dùng tốt"],
        "contexts": ["phòng ngủ", "phòng trọ", "phòng khách", "văn phòng", "mùa nóng"],
        "intents": ["thanh lý bớt đồ", "đổi sang quạt mới", "bán vì chuyển nhà", "nhượng lại nhanh"],
    },
    "kettle": {
        "nouns": ["Ấm đun siêu tốc", "Bình đun nước", "Ấm điện", "Ấm đun mini", "Bình đun gia đình"],
        "specs": ["1.5 lít", "1.8 lít", "tự ngắt", "đế rời", "thân inox"],
        "conditions": ["đun nhanh", "còn sạch", "hoạt động tốt", "ít dùng", "không rò nước"],
        "contexts": ["phòng trọ", "văn phòng", "pha trà cà phê", "gia đình", "mang đi tiện"],
        "intents": ["thanh lý vì có ấm mới", "nhượng lại giá rẻ", "bán do ít dùng", "cần dọn bếp"],
    },
    "lamp": {
        "nouns": ["Đèn bàn", "Đèn ngủ", "Đèn trang trí", "Đèn học", "Đèn làm việc"],
        "specs": ["ánh sáng ấm", "có công tắc", "chân chắc", "đầu đèn linh hoạt", "kiểu nhỏ gọn"],
        "conditions": ["sáng ổn", "còn đẹp", "ít dùng", "dây điện tốt", "đã lau sạch"],
        "contexts": ["bàn học", "bàn làm việc", "phòng ngủ", "decor góc phòng", "đọc sách buổi tối"],
        "intents": ["đổi mẫu đèn mới", "thanh lý bớt đồ", "nhượng lại giá tốt", "bán vì không dùng nữa"],
    },
    "microwave": {
        "nouns": ["Lò vi sóng", "Lò hâm nóng", "Lò vi sóng mini", "Lò vi sóng gia đình", "Lò vi sóng để bàn"],
        "specs": ["dung tích gọn", "có hẹn giờ", "đĩa xoay còn tốt", "nhiều mức công suất", "dễ dùng"],
        "conditions": ["hâm nóng nhanh", "hoạt động ổn", "khoang lò sạch", "ít dùng", "còn dùng tốt"],
        "contexts": ["bếp nhỏ", "văn phòng", "phòng trọ", "hâm cơm hằng ngày", "gia đình"],
        "intents": ["đổi lò lớn hơn", "thanh lý do chuyển nhà", "nhượng lại giá mềm", "bán vì ít nấu"],
    },
    "mirror": {
        "nouns": ["Gương treo tường", "Gương soi", "Gương trang trí", "Gương phòng ngủ", "Gương đứng"],
        "specs": ["mặt gương rõ", "khung còn đẹp", "kích thước vừa", "có móc treo", "kiểu đơn giản"],
        "conditions": ["không nứt vỡ", "còn sáng", "ít xước", "đã lau sạch", "còn dùng tốt"],
        "contexts": ["phòng ngủ", "phòng tắm", "decor căn hộ", "shop nhỏ", "góc trang điểm"],
        "intents": ["đổi gương lớn hơn", "thanh lý do chuyển nhà", "nhượng lại nhanh", "bán vì đổi decor"],
    },
    "oven_stove": {
        "nouns": ["Bếp nướng", "Lò nướng", "Bếp điện", "Bếp gia đình", "Lò bếp nhỏ"],
        "specs": ["nhiệt đều", "có khay", "có hẹn giờ", "còn dây nguồn", "kích thước gọn"],
        "conditions": ["nướng ổn", "hoạt động tốt", "ít dùng", "đã vệ sinh", "còn nóng nhanh"],
        "contexts": ["bếp gia đình", "làm bánh nhỏ", "nấu ăn tại nhà", "phòng trọ", "căn hộ"],
        "intents": ["đổi lò lớn hơn", "thanh lý vì ít dùng", "nhượng lại giá tốt", "cần dọn bếp"],
    },
    "refrigerator": {
        "nouns": ["Tủ lạnh", "Tủ lạnh mini", "Tủ lạnh gia đình", "Tủ mát", "Tủ lạnh nhỏ"],
        "specs": ["làm lạnh tốt", "ngăn đá ổn", "ít hao điện", "kích thước gọn", "2 cánh"],
        "conditions": ["còn lạnh sâu", "chạy êm", "dùng ổn định", "đã vệ sinh", "bên ngoài còn đẹp"],
        "contexts": ["phòng trọ", "gia đình nhỏ", "căn hộ", "văn phòng", "dùng hằng ngày"],
        "intents": ["đổi tủ lớn hơn", "thanh lý do chuyển nhà", "nhượng lại giá tốt", "bán vì không dùng nữa"],
    },
    "sink": {
        "nouns": ["Chậu rửa bếp", "Bồn rửa", "Chậu rửa inox", "Bồn rửa gia đình", "Chậu rửa đơn"],
        "specs": ["inox dày", "có bộ xả", "kích thước vừa", "lòng sâu", "dễ lắp đặt"],
        "conditions": ["còn sáng", "ít trầy", "không thủng", "đã vệ sinh", "dùng ổn"],
        "contexts": ["bếp nhỏ", "sửa bếp gia đình", "nhà trọ", "căn hộ", "bếp phụ"],
        "intents": ["dư sau sửa nhà", "thanh lý do đổi bếp", "nhượng lại nhanh", "bán giá tốt"],
    },
    "faucet": {
        "nouns": ["Vòi nước", "Vòi rửa chén", "Vòi lavabo", "Vòi bếp", "Bộ vòi nước"],
        "specs": ["inox", "cần xoay", "đóng mở nhẹ", "đầu vòi còn tốt", "lắp đặt dễ"],
        "conditions": ["không rò rỉ", "còn sáng", "dùng ổn", "ít trầy", "đã vệ sinh"],
        "contexts": ["khu bếp", "phòng tắm", "sửa nhà", "lắp chậu rửa", "căn hộ"],
        "intents": ["dư sau khi sửa nhà", "đổi mẫu mới", "thanh lý nhanh", "nhượng lại giá mềm"],
    },
    "television": {
        "nouns": ["Tivi", "Smart TV", "Tivi phòng ngủ", "Tivi màn hình phẳng", "Tivi gia đình"],
        "specs": ["32 inch", "40 inch", "có remote", "hình ảnh rõ", "âm thanh ổn"],
        "conditions": ["xem tốt", "màn hình sáng", "chạy ổn định", "ít dùng", "ngoại hình còn đẹp"],
        "contexts": ["phòng ngủ", "phòng khách", "phòng trọ", "xem phim gia đình", "văn phòng"],
        "intents": ["đổi tivi lớn hơn", "thanh lý do chuyển nhà", "nhượng lại giá tốt", "bán vì ít xem"],
    },
    "toaster": {
        "nouns": ["Máy nướng bánh mì", "Lò nướng mini", "Máy nướng sandwich", "Máy nướng bánh", "Lò nướng nhỏ"],
        "specs": ["2 khe", "có khay vụn", "nóng nhanh", "kích thước gọn", "dễ vệ sinh"],
        "conditions": ["nướng ổn", "ít dùng", "còn sạch", "hoạt động tốt", "vỏ máy còn đẹp"],
        "contexts": ["bữa sáng", "bếp nhỏ", "phòng trọ", "văn phòng", "gia đình"],
        "intents": ["thanh lý vì ít dùng", "đổi máy mới", "nhượng lại giá rẻ", "cần dọn bếp"],
    },
    "washing_machine": {
        "nouns": ["Máy giặt", "Máy giặt mini", "Máy giặt cửa trước", "Máy giặt gia đình", "Máy giặt cửa trên"],
        "specs": ["7kg", "8kg", "9kg", "vắt khỏe", "lồng giặt sạch"],
        "conditions": ["giặt vắt ổn", "chạy êm", "còn dùng tốt", "ít lỗi vặt", "đã vệ sinh lồng giặt"],
        "contexts": ["gia đình", "phòng trọ", "căn hộ", "giặt hằng ngày", "nhà nhỏ"],
        "intents": ["đổi máy lớn hơn", "thanh lý do chuyển nhà", "nhượng lại giá tốt", "bán vì ít dùng"],
    },
}

CLASS_GROUP_BY_SOURCE_CLASS: dict[str, str] = {
    "bed": "furniture",
    "cabinet": "furniture",
    "chair": "furniture",
    "table": "furniture",
    "desk": "furniture",
    "sofa": "furniture",
    "blender": "appliance",
    "dishwasher": "appliance",
    "fan": "appliance",
    "kettle": "appliance",
    "lamp": "appliance",
    "microwave": "appliance",
    "mirror": "furniture",
    "oven_stove": "appliance",
    "refrigerator": "large_appliance",
    "sink": "furniture",
    "faucet": "furniture",
    "television": "electronics",
    "toaster": "appliance",
    "washing_machine": "large_appliance",
}

GROUP_DETAIL_DATA: dict[str, dict[str, list[str]]] = {
    "furniture": {
        "details": [
            "gỗ tự nhiên",
            "gỗ ép dày",
            "khung sắt chắc",
            "dáng gọn",
            "màu sáng",
            "màu trung tính",
            "đã lau sạch",
            "ít trầy",
            "phù hợp căn hộ",
            "dễ kê",
        ],
        "secondary": [
            "còn chắc chắn",
            "hợp phòng nhỏ",
            "xem trực tiếp được",
            "dùng hằng ngày",
            "dễ phối nội thất",
            "nhìn sáng nhà",
        ],
    },
    "appliance": {
        "details": [
            "chạy êm",
            "tiết kiệm điện",
            "dễ dùng",
            "đã vệ sinh sạch",
            "còn dây nguồn",
            "vận hành ổn",
            "đủ phụ kiện",
            "ít dùng",
            "nóng nhanh",
            "xay khỏe",
        ],
        "secondary": [
            "phù hợp gia đình",
            "còn hoạt động tốt",
            "xem trực tiếp được",
            "thanh lý gọn",
            "bán vì nâng cấp",
            "dùng hằng ngày",
        ],
    },
    "large_appliance": {
        "details": [
            "7kg",
            "8kg",
            "9kg",
            "2 cánh",
            "mini",
            "còn lạnh sâu",
            "vận hành ổn định",
            "ít hao điện",
            "chạy êm",
            "đủ chức năng",
        ],
        "secondary": [
            "phù hợp gia đình nhỏ",
            "dùng hằng ngày",
            "xem trực tiếp được",
            "bán vì nâng cấp",
            "thanh lý gọn",
        ],
    },
    "electronics": {
        "details": [
            "32 inch",
            "40 inch",
            "còn remote",
            "hình ảnh rõ",
            "âm thanh ổn",
            "chạy êm",
            "đèn sáng tốt",
            "ít dùng",
            "dễ thao tác",
            "bắt sóng tốt",
        ],
        "secondary": [
            "phù hợp phòng ngủ",
            "phù hợp phòng khách",
            "xem trực tiếp được",
            "bán vì đổi mẫu mới",
            "đang dùng ổn",
        ],
    },
}


TITLE_PATTERNS = [
    "{noun} {spec} {detail}, {condition}",
    "{noun} {detail}, {intent}",
    "{noun} {spec}, {secondary}",
    "{noun} {detail} cho {context}",
    "{noun} {spec} cho {context}",
    "{noun} {detail}, {condition}",
    "{noun} {spec} {detail}, {intent}",
    "{noun} {secondary}, phù hợp {context}",
    "{noun} {condition} cho {context}",
]


DESCRIPTION_PATTERNS = [
    "{noun} đang dùng trong nhà, {detail}. Đồ {condition}, phù hợp cho {context}; ai cần có thể xem trực tiếp và trao đổi thêm.",
    "Mình cần nhượng lại {noun_lower} vì {intent}. {noun} {detail}, {secondary}, giá có thể thương lượng nhẹ.",
    "{noun} {spec}, {detail}. Hợp với {context}, mình ưu tiên người qua lấy sớm.",
    "Đồ nhà dùng thật, {condition}. {noun} phù hợp cho {context}, bán vì {intent}.",
    "{noun} {detail}, {secondary}. Mô tả đúng tình trạng thực tế, ai cần có thể liên hệ để xem thêm.",
]


def _stable_index(source_class: str, product_index: int, salt: str, modulo: int) -> int:
    seed = f"{source_class}:{product_index}:{salt}".encode("utf-8")
    value = int(hashlib.sha256(seed).hexdigest()[:12], 16)
    return value % modulo


def _pick(data: dict[str, list[str]], key: str, source_class: str, product_index: int) -> str:
    options = data[key]
    return options[_stable_index(source_class, product_index, key, len(options))]


def _pick_group_detail(source_class: str, product_index: int, key: str) -> str:
    group = GROUP_DETAIL_DATA[CLASS_GROUP_BY_SOURCE_CLASS[source_class]]
    options = group[key]
    return options[_stable_index(source_class, product_index, key, len(options))]


def _tidy(text: str) -> str:
    text = re.sub(r"\s+", " ", text).strip()
    text = text.replace(" ,", ",")
    replacements = {
        "còn dây nguồn còn dùng tốt": "còn dây nguồn, dùng tốt",
        "cho dùng hằng ngày": "dùng hằng ngày",
        "cho bếp gia đình": "cho căn hộ",
        "Ghế ăn bàn ăn": "Ghế ăn cho bàn ăn",
        "Máy giặt mini 9kg": "Máy giặt 9kg",
        "Vòi rửa chén không rò rỉ cho phòng tắm": "Vòi rửa chén không rò rỉ cho khu bếp",
        "Tivi phòng ngủ 32 inch cho văn phòng": "Tivi phòng ngủ 32 inch còn xem tốt",
        "dùng ổn định dùng hằng ngày": "dùng ổn định, phù hợp gia đình",
        "chạy êm dùng hằng ngày": "chạy êm, dùng hằng ngày",
        "thanh lý gọn bếp gia đình": "thanh lý gọn, phù hợp gia đình",
        "còn hoạt động tốt bếp gia đình": "còn hoạt động tốt, phù hợp bếp gia đình",
        "Tủ lạnh cũ mini": "Tủ lạnh mini",
        "hợp phòng nhỏ phòng khách": "hợp phòng nhỏ cho phòng khách",
        "nhiều mức công suất đổi lò lớn hơn": "nhiều mức công suất, đổi lò lớn hơn",
        "gỗ ép dày đổi sang": "gỗ ép dày, đổi sang",
        "còn dây nguồn đổi máy mới": "còn dây nguồn, đổi máy mới",
    }
    for old, new in replacements.items():
        text = text.replace(old, new)
    return text[:1].upper() + text[1:] if text else text


def generate_natural_listing(source_class: str, product_index: int, batch_tag: str = "") -> tuple[str, str]:
    data = TITLE_DATA[source_class]
    pieces = {
        "noun": _pick(data, "nouns", source_class, product_index),
        "spec": _pick(data, "specs", source_class, product_index),
        "condition": _pick(data, "conditions", source_class, product_index),
        "context": _pick(data, "contexts", source_class, product_index),
        "intent": _pick(data, "intents", source_class, product_index),
        "detail": _pick_group_detail(source_class, product_index, "details"),
        "secondary": _pick_group_detail(source_class, product_index, "secondary"),
    }
    pieces["noun_lower"] = pieces["noun"].lower()

    salt_suffix = batch_tag or "default"
    title_template = TITLE_PATTERNS[
        _stable_index(source_class, product_index, f"title-pattern:{salt_suffix}", len(TITLE_PATTERNS))
    ]
    description_template = DESCRIPTION_PATTERNS[
        _stable_index(source_class, product_index, f"description-pattern:{salt_suffix}", len(DESCRIPTION_PATTERNS))
    ]

    title = _tidy(title_template.format(**pieces))
    description = _tidy(description_template.format(**pieces))
    return title, description
