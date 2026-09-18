from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Dict, Any, TypedDict
import json
import os

# LangChain & LangGraph 导入
from langchain_community.vectorstores import Chroma
from langchain_openai import OpenAIEmbeddings, ChatOpenAI
from langchain_community.document_loaders import JSONLoader
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_core.documents import Document
from langchain_core.prompts import ChatPromptTemplate
from langgraph.graph import StateGraph, END

app = FastAPI()

# ============ 配置 ============
# DeepSeek 配置（和 OpenAI 接口兼容）
# 从环境变量读取，不要硬编码 API Key
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1")

# ============ 初始化 DeepSeek LLM ============
llm = ChatOpenAI(
    model="deepseek-v4-flash",  # 或 deepseek-v4-pro
    api_key=DEEPSEEK_API_KEY,
    base_url=DEEPSEEK_BASE_URL,
    temperature=0.3
)
print("✅ 使用 DeepSeek 模型")


# ============ 加载历史工单数据 ============
def load_tickets():
    with open("data/tickets.json", "r", encoding="utf-8") as f:
        return json.load(f)


tickets = load_tickets()


# ============ RAG 向量库（Mock 版） ============
class SimpleVectorStore:
    """Mock 向量库，不依赖 OpenAI"""

    def __init__(self, tickets):
        self.tickets = tickets

    def similarity_search(self, query: str, k: int = 3):
        """简单关键词匹配检索"""
        query_lower = query.lower()
        scored = []
        for ticket in self.tickets:
            text = (ticket["title"] + " " + ticket["description"] + " " + ticket["solution"]).lower()
            score = sum(1 for word in query_lower.split() if word in text)
            scored.append((score, ticket))
        scored.sort(key=lambda x: x[0], reverse=True)
        return [t for _, t in scored[:k]]


vector_store = SimpleVectorStore(tickets)
print("✅ 使用 Mock 向量库（关键词匹配）")


# ============ LangGraph 状态定义 ============
class AgentState(TypedDict):
    title: str
    description: str
    category: str
    retrieved_docs: List[Dict]
    confidence: float
    options: List[Dict]
    suggested: str
    summary: str
    status: str


# ============ LangGraph 节点函数 ============
def classify_intent(state: AgentState) -> AgentState:
    """节点1：DeepSeek 意图分类"""
    prompt = ChatPromptTemplate.from_template("""
        你是一个IT工单分类专家。请根据以下工单内容，判断属于哪个分类。

        分类选项（只能选一个）：
        1. 网络问题 - WiFi、VPN、网线、连接、断网、上网
        2. 账号问题 - 密码、登录、邮箱、权限、AD域
        3. 设备故障 - 蓝屏、死机、打印机、电脑、开机、黑屏
        4. 人事审批 - 请假、审批、报销、人事、加班、出差

        工单标题：{title}
        工单描述：{description}

        只返回分类名称（网络问题/账号问题/设备故障/人事审批），不要输出其他内容。
    """)

    chain = prompt | llm
    response = chain.invoke({
        "title": state["title"],
        "description": state["description"]
    })

    category = response.content.strip()
    valid_categories = ["网络问题", "账号问题", "设备故障", "人事审批"]
    if category not in valid_categories:
        # 尝试从内容中提取
        for vc in valid_categories:
            if vc in category:
                category = vc
                break
        else:
            category = "人事审批"

    state["category"] = category
    print(f"[DeepSeek] 分类结果: {category}")
    return state


def retrieve_docs(state: AgentState) -> AgentState:
    """节点2：RAG 检索"""
    query = state["title"] + " " + state["description"]
    docs = vector_store.similarity_search(query, k=3)
    state["retrieved_docs"] = docs
    print(f"[RAG] 检索到 {len(docs)} 条历史工单")
    return state


def generate_plan(state: AgentState) -> AgentState:
    """节点3：DeepSeek 生成方案 + 决定状态"""
    category = state["category"]

    # 准备历史工单上下文
    docs = state.get("retrieved_docs", [])
    history_text = ""
    if docs:
        for i, doc in enumerate(docs[:3]):
            history_text += f"{i + 1}. {doc.get('solution', '')}\n"
    if not history_text:
        history_text = "暂无相似历史工单"

    # 用 DeepSeek 生成方案
    prompt = ChatPromptTemplate.from_template("""
        你是一个IT工单处理专家。请根据以下信息，生成处理方案。

        工单分类：{category}
        工单标题：{title}
        工单描述：{description}
        历史相似工单解决方案：{history}

        请生成3个备选方案，每个方案包含：
        - id: opt_1, opt_2, opt_3
        - title: 方案名称（简短）
        - description: 详细描述
        - successRate: 成功率（数字，60-95之间）

        按成功率从高到低排序。

        只返回 JSON 数组，不要有其他内容。
        格式示例：
        [{{"id": "opt_1", "title": "重启路由器", "description": "重启AP设备，恢复网络", "successRate": 85}}]
    """)

    chain = prompt | llm
    response = chain.invoke({
        "category": category,
        "title": state["title"],
        "description": state["description"],
        "history": history_text
    })

    try:
        content = response.content
        # 去掉可能的 markdown 标记
        if "```json" in content:
            content = content.split("```json")[1].split("```")[0]
        elif "```" in content:
            content = content.split("```")[1].split("```")[0]
        options = json.loads(content.strip())
        state["options"] = options
        state["suggested"] = options[0]["id"]
        print(f"[DeepSeek] 生成 {len(options)} 个方案")
    except Exception as e:
        print(f"[DeepSeek] 方案生成失败，使用默认方案: {e}")
        # 默认方案
        options_map = {
            "网络问题": [
                {"id": "opt_1", "title": "重启路由器", "description": "重启AP设备，恢复网络", "successRate": 85},
                {"id": "opt_2", "title": "检查IP配置", "description": "检查DHCP分配是否正常", "successRate": 60},
                {"id": "opt_3", "title": "更换网线", "description": "更换物理网线测试", "successRate": 40}
            ],
            "账号问题": [
                {"id": "opt_1", "title": "重置密码", "description": "通过邮箱或手机重置密码", "successRate": 90},
                {"id": "opt_2", "title": "解锁账号", "description": "联系管理员手动解锁", "successRate": 70}
            ],
            "设备故障": [
                {"id": "opt_1", "title": "重启设备", "description": "重启电脑或设备", "successRate": 75},
                {"id": "opt_2", "title": "更新驱动", "description": "检查并更新驱动程序", "successRate": 50},
                {"id": "opt_3", "title": "系统还原", "description": "还原到上一个正常状态", "successRate": 65}
            ],
            "人事审批": [
                {"id": "opt_1", "title": "人工处理", "description": "等待人工审批", "successRate": 100}
            ]
        }
        state["options"] = options_map.get(category, options_map["人事审批"])
        state["suggested"] = state["options"][0]["id"]

    # 生成摘要
    if docs:
        top_doc = docs[0]
        state["summary"] = f"根据历史记录，类似工单的解决方案是：{top_doc.get('solution', '')}"
    else:
        state["summary"] = f"经DeepSeek分析，这是{category}，系统已生成备选方案"

    # 根据工单内容动态计算置信度
    desc = state["description"]
    title = state["title"]
    full_text = title + " " + desc

    # 1. 描述长度：越详细置信度越高
    desc_len = len(desc)

    # 2. 是否有明确的问题关键词
    clear_keywords = ["无法", "不能", "报错", "黑屏", "蓝屏", "无法连接", "密码",
                      "登录", "打印机", "WiFi", "断网", "卡顿", "闪退", "超时"]
    keyword_hit = sum(1 for kw in clear_keywords if kw in full_text)

    # 3. 检索到的相似工单数量
    similar_count = len(docs)

    # 综合计算置信度
    if desc_len >= 30 and keyword_hit >= 2 and similar_count >= 2:
        # 描述详细 + 多个关键词 + 有历史相似工单 → 高置信度
        state["confidence"] = 0.88
    elif desc_len >= 15 and keyword_hit >= 1:
        # 描述中等 + 有明确关键词 → 较高置信度
        state["confidence"] = 0.75
    elif desc_len >= 10:
        # 描述较短 → 中等置信度
        state["confidence"] = 0.6
    else:
        # 描述太模糊 → 低置信度
        state["confidence"] = 0.4

    # 决定状态
    if state["confidence"] >= 0.8:
        state["status"] = "SUCCESS"
    else:
        state["status"] = "WAITING_HUMAN"

    print(f"[DeepSeek] 最终状态: {state['status']}, 置信度: {state['confidence']}")
    return state


# ============ 构建 LangGraph ============
workflow = StateGraph(AgentState)

workflow.add_node("classify", classify_intent)
workflow.add_node("retrieve", retrieve_docs)
workflow.add_node("generate_plan", generate_plan)

workflow.set_entry_point("classify")
workflow.add_edge("classify", "retrieve")
workflow.add_edge("retrieve", "generate_plan")
workflow.add_edge("generate_plan", END)

agent = workflow.compile()


# ============ FastAPI 接口 ============
class AnalyzeRequest(BaseModel):
    ticketId: str
    title: str
    description: str


@app.post("/analyze")
def analyze(req: AnalyzeRequest):
    try:
        result = agent.invoke({
            "title": req.title,
            "description": req.description,
            "category": "",
            "retrieved_docs": [],
            "confidence": 0.0,
            "options": [],
            "suggested": "",
            "summary": "",
            "status": ""
        })

        return {
            "code": 200,
            "data": {
                "status": result["status"],
                "category": result["category"],
                "confidence": result["confidence"],
                "options": result["options"],
                "suggested": result["suggested"],
                "summary": result["summary"]
            }
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/health")
def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)