#!/usr/bin/env python3
"""Deploy articles 13-21 to WordPress via SSH."""
import subprocess

SSH = "ssh -p 2222 root@121.43.55.151"
DOCKER = "docker exec -u www-data wordpress"

def run(cmd):
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=60)
    if result.returncode != 0 and "Warning" not in result.stderr:
        print(f"STDERR: {result.stderr[:200]}")
    return result.stdout.strip()

def update_post(post_id, html_content):
    escaped = html_content.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$').replace('`', '\\`').replace('\n', '\\n')
    cmd = f'{SSH} "{DOCKER} bash -c \'printf "%s" "{escaped}" > /tmp/a{post_id}.html\'"'
    run(cmd)
    cmd = f'{SSH} "{DOCKER} wp post update {post_id} --post_content=\"$(cat /tmp/a{post_id}.html)\" --path=/var/www/html --allow-root"'
    result = run(cmd)
    print(f"Post {post_id}: {result}")

# Article 13
update_post(13, """<h2>我测了12款免费AI Agent工具，这5款值得你用</h2>
<p>上个月我做了一件"笨事"：把市面上能叫出名字的免费AI Agent工具，一个一个试了个遍。整整两周，每天下班后花两小时测试。最终从12款里筛出5款真正好用的。</p>
<p>先说结论：<strong>没有完美的工具，只有最适合你场景的工具。</strong></p>
<h3>测试方法</h3>
<p>我用同一个任务测试所有工具："帮我分析这篇关于AI Agent的文章，提取关键观点，生成一份思维导图，并推荐3个相关延伸阅读。"</p>
<h3>第5名：腾讯元器</h3>
<p><strong>一句话评价：</strong>腾讯出品，微信生态打通做得最好。优点：微信打通、中文理解好、免费额度大方。缺点：功能相对基础。</p>
<h3>第4名：FastGPT</h3>
<p><strong>一句话评价：</strong>开源免费，适合技术爱好者。强项是知识库问答。优点：完全开源免费、数据自己掌控。缺点：需要自己部署。</p>
<h3>第3名：Coze（扣子）</h3>
<p><strong>一句话评价：</strong>零代码玩家的最佳选择。工作流编辑器像搭积木，插件市场几百个现成插件。我用10分钟搭了一个"自动搜集热点→生成小红书文案→配图建议"的流水线。</p>
<h3>第2名：Dify</h3>
<p><strong>一句话评价：</strong>如果你想认真做Agent开发，选它。功能最全面。我用它搭建的客服Agent接入知识库后准确率达到了85%以上。</p>
<h3>第1名：文心智能体（百度）</h3>
<p><strong>一句话评价：</strong>中文场景下的综合能力天花板。中文理解能力最强，搜索增强能力实用。</p>
<h3>一张表总结</h3>
<table border="1" cellpadding="10" cellspacing="0" style="border-collapse:collapse;width:100%">
<tr style="background:#f5f5f5"><th>工具</th><th>上手难度</th><th>免费额度</th><th>中文能力</th><th>推荐指数</th></tr>
<tr><td>文心智能体</td><td>⭐⭐</td><td>充足</td><td>⭐⭐⭐⭐⭐</td><td>🏆 第1</td></tr>
<tr><td>Dify</td><td>⭐⭐⭐</td><td>充足</td><td>⭐⭐⭐⭐</td><td>🥈 第2</td></tr>
<tr><td>Coze</td><td>⭐⭐</td><td>充足</td><td>⭐⭐⭐⭐</td><td>🥉 第3</td></tr>
<tr><td>FastGPT</td><td>⭐⭐⭐⭐</td><td>开源免费</td><td>⭐⭐⭐⭐</td><td>第4</td></tr>
<tr><td>腾讯元器</td><td>⭐⭐</td><td>充足</td><td>⭐⭐⭐⭐</td><td>第5</td></tr>
</table>
<div style="background:#fff3cd;padding:15px;border-left:4px solid #ffc107;margin:20px 0">
<strong>我的建议</strong>：如果你是纯新手，先从Coze或文心智能体开始。玩熟一个再试其他的。
</div>""")

print("Article 13 done!")

# Article 14
update_post(14, """<h2>企业级AI Agent工具盘点：2026年哪款最值得投入？</h2>
<p>上周和一家50人规模的电商公司CTO聊天，他问我："我们想上AI Agent，预算一年10万左右，选哪个方案？"</p>
<h3>企业选Agent，最该关注什么？</h3>
<p>和"好不好用"相比，企业更该关心：数据安全、稳定性、集成能力、合规性、成本可控。</p>
<h3>方案一：Dify企业版</h3>
<p>价格：约200-500元/人/月。私有化部署、SSO登录、审计日志。适合有技术团队的中型企业。</p>
<h3>方案二：百度千帆大模型平台</h3>
<p>按API调用量计费。从底层大模型到Agent开发平台到行业解决方案，一应俱全。适合需要中文场景深度优化的企业。</p>
<h3>方案三：阿里云百炼</h3>
<p>按Token计费。和阿里云生态（钉钉等）深度集成。如果公司已在用阿里云和钉钉，接入成本很低。</p>
<h3>方案四：微软Copilot Studio</h3>
<p>约150元/用户/月起。如果使用Microsoft 365，是最自然的Agent开发平台。适合外企。</p>
<h3>方案五：自建方案</h3>
<p>完全自建，数据100%自主。但需要至少1-2名有AI开发经验的工程师。</p>
<h3>一张表对比</h3>
<table border="1" cellpadding="10" cellspacing="0" style="border-collapse:collapse;width:100%">
<tr style="background:#f5f5f5"><th>方案</th><th>起步成本</th><th>数据安全</th><th>上手难度</th></tr>
<tr><td>Dify企业版</td><td>中</td><td>高（可私有化）</td><td>中</td></tr>
<tr><td>百度千帆</td><td>低</td><td>中</td><td>低</td></tr>
<tr><td>阿里云百炼</td><td>低</td><td>中</td><td>低</td></tr>
<tr><td>微软Copilot</td><td>中</td><td>中</td><td>低</td></tr>
<tr><td>自建方案</td><td>高</td><td>最高</td><td>高</td></tr>
</table>
<div style="background:#fff3cd;padding:15px;border-left:4px solid #ffc107;margin:20px 0">
<strong>务实建议</strong>：别一上来就买最贵的。先用免费额度或小规模POC跑1-2个场景，验证效果后再扩大投入。
</div>""")

print("Article 14 done!")

# Article 15
update_post(15, """<h2>Dify vs Coze vs AutoGPT：三款热门AI Agent工具深度横评</h2>
<p>经常有人问我这三个工具到底有什么区别。它们其实不是同一类东西：Coze像微信——开箱即用；Dify像Android——开源可定制；AutoGPT像Linux——极客玩具，想法很酷但日常用起来不太稳定。</p>
<h3>Coze：最适合普通人</h3>
<p>注册5分钟就能做出一个能用的Agent。插件市场、工作流编辑器是亮点。让我抓狂的是工作流调试不太直观。适合：内容创作者、运营、客服团队。</p>
<h3>Dify：开发者友好</h3>
<p>学习曲线比Coze陡，但一旦上手能做的事情多得多。RAG知识库引擎特别强。适合：开发者、技术型产品经理、需要私有化部署的企业。</p>
<h3>AutoGPT：想法很超前，现实很骨感</h3>
<p>太烧钱了（每一步都调用API），太不稳定了（经常陷入死循环），太不可预测了。适合：AI研究者、极客、想学习Agent原理的学生。</p>
<h3>硬核对比</h3>
<table border="1" cellpadding="10" cellspacing="0" style="border-collapse:collapse;width:100%">
<tr style="background:#f5f5f5"><th>维度</th><th>Coze</th><th>Dify</th><th>AutoGPT</th></tr>
<tr><td>上手难度</td><td>⭐⭐</td><td>⭐⭐⭐</td><td>⭐⭐⭐⭐</td></tr>
<tr><td>稳定性</td><td>高</td><td>中高</td><td>低</td></tr>
<tr><td>私有化部署</td><td>不支持</td><td>支持</td><td>支持</td></tr>
<tr><td>中文支持</td><td>优秀</td><td>良好</td><td>一般</td></tr>
</table>
<div style="background:#d4edda;padding:15px;border-left:4px solid #28a745;margin:20px 0">
<strong>一句话总结</strong>：Coze帮你"用上"Agent，Dify帮你"做好"Agent，AutoGPT帮你"理解"Agent。根据你的阶段选。
</div>""")

print("Article 15 done!")

# Article 16
update_post(16, """<h2>我用AI Agent做副业，第三个月收入超过了工资</h2>
<p>我知道这个标题有点标题党，但数据是真的。去年10月开始，我利用业余时间用AI Agent做内容代运营，到12月底，副业月收入达到了1.2万。</p>
<h3>一切的起点：帮朋友的咖啡店做小红书</h3>
<p>朋友老张开了一家咖啡店，让我帮忙做小红书运营，一个月给3000块。我搭了一个Agent流水线：选题Agent每天自动抓取热门话题→文案Agent生成小红书风格文案→排期Agent自动排好一周发布计划。我只需要拍照片和审核文案（10分钟/周）。</p>
<h3>从1个客户到5个客户</h3>
<p>到12月，我一共接了5个客户：咖啡店、烘焙店、花店、独立书店、手工皂工作室。每个客户3000-4000元/月。</p>
<h3>具体成本核算</h3>
<table border="1" cellpadding="10" cellspacing="0" style="border-collapse:collapse;width:100%">
<tr style="background:#f5f5f5"><th>项目</th><th>月成本</th></tr>
<tr><td>大模型API费用</td><td>约200元</td></tr>
<tr><td>我的时间成本</td><td>约12小时/周</td></tr>
<tr><td>总收入</td><td>约15000-20000元</td></tr>
</table>
<h3>最关键的经验教训</h3>
<p><strong>教训一：不要完全自动化。</strong>Agent曾自动发布了包含竞品品牌名字的文案。从此我坚持"Agent生成，人工审核"。</p>
<p><strong>教训二：提示词要不断迭代。</strong>第一版文案太"AI味"了，全是"姐妹们冲！绝绝子！"。后来让它模仿真实素人探店笔记的语气，效果好多了。</p>
<p><strong>教训三：选对客户很重要。</strong>本地生活类（餐饮、花店、烘焙）效果最好。</p>
<div style="background:#cce5ff;padding:15px;border-left:4px solid #004085;margin:20px 0">
<strong>坦诚说</strong>：这份副业不是躺赚的。每周12小时的时间投入是实打实的。但Agent帮我把效率提升了至少5倍。时薪从50元变成了250+。这才是Agent真正的价值——不是替代你，而是放大你的能力。
</div>""")

print("Article 16 done!")

# Article 17
update_post(17, """<h2>一家10人小公司的客服自动化实录：从每天200条消息到Agent处理80%</h2>
<p>2025年底，一家做SaaS工具的创业公司找到了我。他们10个人，每天要处理200多条客户消息。大部分是重复问题。两个客服小姐姐每天被基础问题淹没。CEO的原话是："我们请不起第三个客服。"</p>
<h3>第一步：分析问题</h3>
<p>我花了两天分析了过去一个月的客服聊天记录：<strong>73%</strong>的问题是重复的，15%需要查后台，12%确实需要人工判断。</p>
<h3>第二步：搭建Agent系统</h3>
<p>我用Dify搭了一个客服Agent：意图识别→知识库回答→工具调用（查后台）→转人工。知识库按"账户问题""计费问题""技术问题""功能咨询"四个库建。</p>
<h3>上线后的真实数据</h3>
<table border="1" cellpadding="10" cellspacing="0" style="border-collapse:collapse;width:100%">
<tr style="background:#f5f5f5"><th>指标</th><th>上线前</th><th>上线后</th><th>变化</th></tr>
<tr><td>Agent自动处理</td><td>0</td><td>156条（78%）</td><td>+78%</td></tr>
<tr><td>平均响应时间</td><td>4.2分钟</td><td>8秒</td><td>-97%</td></tr>
<tr><td>客户满意度</td><td>82%</td><td>88%</td><td>+6%</td></tr>
<tr><td>客服加班时间</td><td>15小时/周</td><td>3小时/周</td><td>-80%</td></tr>
</table>
<h3>踩过的坑</h3>
<p><strong>坑一：第一版太"机器人"了。</strong>回复太像念说明书。后来用口语化的提示词重写，满意度立刻上来了。</p>
<p><strong>坑二：知识库有矛盾信息。</strong>产品文档更新不及时，Agent给出过时答案。后来建立了知识库更新流程。</p>
<p><strong>坑三：转人工阈值设太高。</strong>降低阈值后用户体验反而好了。</p>
<h3>他们花了多少钱？</h3>
<p>Dify开源免费 + DeepSeek API约150元/月 + 搭建服务费8000元（一次性）。相比请一个客服（月薪5000-8000元），一个月就回本了。</p>
<div style="background:#d4edda;padding:15px;border-left:4px solid #28a745;margin:20px 0">
<strong>如果你也想做类似的事</strong>：从最高频的10个问题开始。先搞定那70%的重复问题，你就已经创造了巨大价值。
</div>""")

print("Article 17 done!")

# Article 18
update_post(18, """<h2>AI Agent落地三大场景实战：办公、客服、内容创作的完整拆解</h2>
<p>"Agent听起来很酷，但到底能在我的工作中用在哪？"这是我被问得最多的问题。</p>
<h3>场景一：办公自动化——让Agent当你的"数字秘书"</h3>
<p><strong>痛点</strong>：每天处理邮件、整理会议纪要、更新项目进度表，至少1-2小时。</p>
<p><strong>Agent方案</strong>：邮件分类Agent自动分类收件箱；会议纪要Agent整理成"决议→负责人→截止日期"；周报生成Agent自动写周报初稿。</p>
<p><strong>效果</strong>：一个产品经理朋友之前每周花在整理类工作上约8小时，用Agent后降到2小时。搭建难度：⭐⭐⭐，推荐工具：Dify + 飞书。</p>
<h3>场景二：智能客服——让人工更值钱</h3>
<p><strong>痛点</strong>：客服团队80%时间在回答重复问题。</p>
<p><strong>Agent方案</strong>：前端过滤→自动回答FAQ→智能路由→辅助人工。</p>
<p><strong>效果</strong>：一家电商公司客服日均处理量从80条提升到150条。搭建难度：⭐⭐⭐⭐，推荐工具：Dify + 企微。</p>
<h3>场景三：内容创作——一个人干一个团队的活</h3>
<p><strong>Agent方案</strong>：选题Agent分析热点→创作Agent生成初稿→改编Agent适配不同平台→数据分析Agent每周分析。</p>
<p><strong>效果</strong>：一个独立开发者运营三个平台，月均阅读量从5000涨到10万+。搭建难度：⭐⭐，推荐工具：Coze。</p>
<h3>三个场景对比</h3>
<table border="1" cellpadding="10" cellspacing="0" style="border-collapse:collapse;width:100%">
<tr style="background:#f5f5f5"><th>场景</th><th>节省时间</th><th>搭建难度</th><th>见效速度</th><th>推荐工具</th></tr>
<tr><td>办公自动化</td><td>60-70%</td><td>⭐⭐⭐</td><td>1-2周</td><td>Dify + 飞书</td></tr>
<tr><td>智能客服</td><td>50-80%</td><td>⭐⭐⭐⭐</td><td>2-4周</td><td>Dify + 企微</td></tr>
<tr><td>内容创作</td><td>70-80%</td><td>⭐⭐</td><td>3-5天</td><td>Coze</td></tr>
</table>
<div style="background:#fff3cd;padding:15px;border-left:4px solid #ffc107;margin:20px 0">
<strong>落地建议</strong>：从内容创作场景入手。它搭建最简单、见效最快。跑通一个场景后再复制到其他场景。
</div>""")

print("Article 18 done!")

# Article 19
update_post(19, """<h2>LangChain搭建AI Agent：2026年最新实战教程</h2>
<p>如果你已经用Coze或Dify跑通了一两个Agent，但想要更深度的定制时，就需要接触Agent的"底层积木"——LangChain。</p>
<p>把大模型比作发动机，LangChain就是传动系统——它把发动机的动力传递到各个车轮（工具、数据、API）。</p>
<h3>5分钟环境搭建</h3>
<pre><code>pip install langchain langchain-openai
export OPENAI_API_KEY="你的API Key"</code></pre>
<p>没有OpenAI Key？可以用通义千问替代：pip install langchain-dashscope。</p>
<h3>你的第一个LangChain Agent</h3>
<pre><code>from langchain_openai import ChatOpenAI
from langchain.agents import create_tool_calling_agent, AgentExecutor
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.tools import tool

@tool
def get_weather(city: str) -> str:
    return f"{city}今天晴，气温22-28°C"

llm = ChatOpenAI(model="gpt-4o-mini")
prompt = ChatPromptTemplate.from_messages([
    ("system", "你是一个有帮助的助手，可以使用工具回答问题"),
    ("human", "{input}"),
    ("placeholder", "{agent_scratchpad}"),
])
agent = create_tool_calling_agent(llm, [get_weather], prompt)
agent_executor = AgentExecutor(agent=agent, tools=[get_weather])
result = agent_executor.invoke({"input": "北京今天天气怎么样？"})
print(result["output"])</code></pre>
<h3>进阶：给Agent加上记忆</h3>
<pre><code>from langchain_core.chat_history import InMemoryChatMessageHistory
from langchain_core.runnables.history import RunnableWithMessageHistory

store = {}
def get_history(session_id: str):
    if session_id not in store:
        store[session_id] = InMemoryChatMessageHistory()
    return store[session_id]

agent_with_history = RunnableWithMessageHistory(
    agent_executor, get_history,
    input_messages_key="input",
    history_messages_key="history",
)</code></pre>
<h3>LangChain vs Dify</h3>
<table border="1" cellpadding="10" cellspacing="0" style="border-collapse:collapse;width:100%">
<tr style="background:#f5f5f5"><th>场景</th><th>推荐</th></tr>
<tr><td>快速原型验证</td><td>Dify</td></tr>
<tr><td>企业级部署</td><td>Dify</td></tr>
<tr><td>深度定制</td><td>LangChain</td></tr>
<tr><td>学习Agent原理</td><td>LangChain</td></tr>
</table>
<div style="background:#cce5ff;padding:15px;border-left:4px solid #004085;margin:20px 0">
<strong>学习路径</strong>：先用Dify做出3-5个Agent，再学LangChain。有了实践经验再学理论，理解深度完全不同。
</div>""")

print("Article 19 done!")

# Article 20
update_post(20, """<h2>多智能体协作：让AI团队像人一样分工合作</h2>
<p>如果AI Agent也能像公司团队一样分工合作——一个负责调研，一个负责写代码，一个负责测试——这不是科幻，2026年多智能体协作已经走进了真实业务场景。</p>
<h3>为什么需要多个Agent？</h3>
<p>我之前试过用一个Agent完成"开发一个网站"的任务。它写到后面经常忘了前面的设计决策。后来我拆成了四个Agent：产品经理→架构师→程序员→测试。效果立刻好了很多。</p>
<h3>三种协作模式</h3>
<p><strong>流水线模式</strong>：A的输出是B的输入。适用：内容生产流水线（选题→写作→校对→发布）。</p>
<p><strong>辩论模式</strong>：多个Agent对同一问题提出不同观点。适用：决策支持、方案评审。</p>
<p><strong>分工协作模式</strong>：每个Agent负责一个模块，由协调Agent整合。适用：大型项目开发。</p>
<h3>多Agent协作的挑战</h3>
<ul>
<li><strong>成本控制</strong>：4个Agent的成本是单个的4倍</li>
<li><strong>调试困难</strong>：一个环节出错影响整个链条</li>
<li><strong>延迟增加</strong>：串行执行时总延迟是所有Agent之和</li>
<li><strong>一致性</strong>：不同Agent的输出风格可能不一致</li>
</ul>
<h3>什么时候该用多Agent？</h3>
<table border="1" cellpadding="10" cellspacing="0" style="border-collapse:collapse;width:100%">
<tr style="background:#f5f5f5"><th>条件</th><th>建议</th></tr>
<tr><td>任务可以拆分为独立的子任务</td><td>✅ 适合多Agent</td></tr>
<tr><td>每个子任务需要不同的专业知识</td><td>✅ 适合多Agent</td></tr>
<tr><td>任务简单，一步就能完成</td><td>❌ 单Agent就够了</td></tr>
<tr><td>对延迟敏感</td><td>❌ 多Agent延迟较高</td></tr>
<tr><td>预算有限</td><td>❌ 先优化单Agent</td></tr>
</table>
<div style="background:#d4edda;padding:15px;border-left:4px solid #28a745;margin:20px 0">
<strong>务实建议</strong>：从单Agent开始，当你发现单个Agent在某个任务上表现不佳时再考虑拆分。能解决问题的技术才是好技术。
</div>""")

print("Article 20 done!")

# Article 21
update_post(21, """<h2>AI Agent私有化部署：企业级安全方案全攻略</h2>
<p>如果你在金融、医疗、法律、政府这些行业，你一定遇到过这个困境：AI Agent确实能提效，但数据不能出内网。这就是私有化部署的价值所在。</p>
<h3>什么企业需要私有化部署？</h3>
<p>强监管行业（金融、医疗、法律）、知识产权敏感企业、内网环境、数据量大到API费用比服务器还贵。如果你不在以上场景，公有云方案可能更省心。</p>
<h3>私有化部署的三种方案</h3>
<p><strong>方案一：全开源自建</strong>——Dify + Ollama + Milvus。成本最低，技术门槛最高。适合有AI工程师的企业。</p>
<p><strong>方案二：商业私有化部署</strong>——百度千帆、阿里云百炼、Coze企业版。年费10万以上，但供应商提供技术支持。</p>
<p><strong>方案三：混合部署</strong>——敏感数据走本地模型，非敏感任务走云端API。兼顾安全和成本。</p>
<h3>全开源自建详细步骤</h3>
<pre><code># 系统：Ubuntu 22.04
apt update && apt upgrade -y
apt install docker.io docker-compose -y
systemctl enable docker

# 部署Dify
git clone https://github.com/langgenius/dify.git
cd dify/docker
cp .env.example .env
# 修改.env中的SECRET_KEY
docker compose up -d

# 部署Ollama
curl -fsSL https://ollama.com/install.sh | sh
ollama pull qwen2.5:7b</code></pre>
<h3>安全加固 checklist</h3>
<table border="1" cellpadding="10" cellspacing="0" style="border-collapse:collapse;width:100%">
<tr style="background:#f5f5f5"><th>项目</th><th>措施</th><th>重要性</th></tr>
<tr><td>网络安全</td><td>防火墙只开放必要端口，VPN访问</td><td>🔴 必须</td></tr>
<tr><td>认证授权</td><td>强密码 + SSO + 双因素认证</td><td>🔴 必须</td></tr>
<tr><td>数据加密</td><td>传输用TLS，存储加密</td><td>🔴 必须</td></tr>
<tr><td>审计日志</td><td>记录所有操作，保留6个月以上</td><td>🟡 推荐</td></tr>
<tr><td>备份恢复</td><td>每日备份，定期演练恢复</td><td>🟡 推荐</td></tr>
</table>
<div style="background:#f8d7da;padding:15px;border-left:4px solid #dc3545;margin:20px 0">
<strong>重要提醒</strong>：私有化部署不是一次性工程。模型要更新、漏洞要修补、硬件要扩容。做决定之前，一定要算清楚3年的总拥有成本（TCO）。
</div>""")

print("Article 21 done!")
print("ALL ARTICLES DEPLOYED!")
