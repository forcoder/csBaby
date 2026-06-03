# csBaby Wiki Schema

## Wiki Configuration

```json
{
  "wiki": {
    "name": "csBaby Wiki",
    "description": "客服小秘智能客服APP知识库",
    "version": "1.0.0",
    "root": "wiki"
  },
  "categories": [
    "Architecture",
    "Core Systems",
    "Features",
    "Data Layer",
    "Development"
  ],
  "articles": 17,
  "topics": 5
}
```

## Naming Conventions

- Article files: `Pascal-Case.md`
- Wikilinks: `[[Article-Name]]`
- Categories from index.md section headings

## Graph Schema

```json
{
  "nodes": {
    "article": {
      "required": ["id", "name", "summary", "tags", "complexity"],
      "optional": ["category", "wikilinks", "content"]
    },
    "topic": {
      "required": ["id", "name", "summary"],
      "optional": ["parentId", "children"]
    },
    "source": {
      "required": ["id", "name", "path"],
      "optional": ["size", "type"]
    }
  },
  "edges": {
    "related": { "source": "article", "target": "article" },
    "categorized_under": { "source": "article", "target": "topic" },
    "derived_from": { "source": "article", "target": "source" }
  }
}
```

## Complexity Levels

| Level | Description |
|-------|-------------|
| basic | 核心概念，仅描述 |
| intermediate | 包含实现细节 |
| advanced | 深度技术分析 |

## Layer Mapping

| Layer | Articles |
|-------|----------|
| Architecture | 3 |
| Core Systems | 6 |
| Features | 2 |
| Data Layer | 3 |
| Development | 2 |