package com.example.yuejie;

import org.json.JSONObject;

final class Book {
  final JSONObject raw;
  final String id, title, author, cover, summary, category, score, words, firstItemId;
  Book(JSONObject o) {
    raw=o; id=o.optString("book_id"); title=o.optString("book_name",o.optString("title")); author=o.optString("author");
    cover=o.optString("thumb_url",o.optString("cover_url")); summary=o.optString("abstract",o.optString("description")); category=o.optString("category");
    score=o.optString("score"); words=o.optString("word_number",o.optString("word_count")); firstItemId=o.optString("first_chapter_item_id");
  }
}
