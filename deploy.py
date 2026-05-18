#!/usr/bin/env python3
"""Deploy articles to WordPress via SSH."""
import subprocess
import sys

SSH = "ssh -p 2222 root@121.43.55.151"
DOCKER = "docker exec -u www-data wordpress"

def run(cmd):
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=60)
    if result.returncode != 0 and "Warning" not in result.stderr:
        print(f"STDERR: {result.stderr[:300]}")
    return result.stdout.strip()

def update_post(post_id, html_content):
    # Write to file in container using tee to avoid heredoc issues
    escaped = html_content.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$').replace('`', '\\`').replace('\n', '\\n')
    cmd = f'{SSH} "{DOCKER} bash -c \'printf "%s" "{escaped}" > /tmp/a{post_id}.html\'"'
    run(cmd)
    # Update the post
    cmd = f'{SSH} "{DOCKER} wp post update {post_id} --post_content=\"$(cat /tmp/a{post_id}.html)\" --path=/var/www/html --allow-root"'
    result = run(cmd)
    print(f"Post {post_id}: {result}")

# Article 10
update_post(10, '<h2>零代码搭建AI Agent：2026年最详细的本地部署实录</h2>\n<p>如果你和我一样，看到命令行就头疼，但又想拥有一个完全掌控的AI Agent——这篇文章就是为你写的。我用了一个周末，零代码在自己的Windows电脑上跑通了一个完整的AI Agent。整个过程不需要写一行代码。</p>\n<h3>为什么要在本地搭建？</h3>\n<ul>\n<li><strong>数据隐私</strong>：客户资料、内部文档传到第三方服务器上，总觉得不踏实</li>\n<li><strong>费用</strong>：API调用量一大，账单吓死人。本地运行，硬件成本是一次性的</li>\n<li><strong>断网可用</strong>：出差高铁上没网？本地Agent照跑不误</li>\n</ul>\n<h3>你需要准备的东西</h3>\n<table border="1" cellpadding="10" cellspacing="0" style="border-collapse:collapse;width:100%">\n<tr style="background:#f5f5f5"><th>项目</th><th>要求</th><th>说明</th></tr>\n<tr><td>电脑</td><td>内存≥8GB，推荐16GB</td><td>跑大模型吃内存，8GB是底线</td></tr>\n<tr><td>系统</td><td>Windows 10/11</td><td>Mac和Linux也行</td></tr>\n<tr><td>时间</td><td>大约2-3小时</td><td>跟着步骤走，不急</td></tr>\n</table>\n<h3>第一步：安装Docker Desktop</h3>\n<p>去 docker.com 下载 Docker Desktop，双击安装，一路下一步。安装完会自动重启电脑。首次启动会提示你登录，可以跳过不登录。</p>\n<h3>第二步：用Dify搭建Agent平台</h3>\n<p>Dify是目前最友好的开源Agent开发平台。它的界面像搭积木一样，拖拖拽拽就能做出一个Agent。</p>\n<ol>\n<li>打开文件资源管理器，地址栏输入 %USERPROFILE%\\dify，回车</li>\n<li>如果没有dify文件夹，新建一个</li>\n<li>在这个文件夹里创建 docker-compose.yaml 文件</li>\n<li>打开PowerShell，运行：cd %USERPROFILE%\\dify &amp;&amp; docker compose up -d</li>\n</ol>\n<p>等几分钟下载完成。打开浏览器访问 http://localhost:3000，看到Dify界面就成功了。</p>\n<h3>第三步：接入本地大模型（Ollama）</h3>\n<ol>\n<li>去 ollama.com 下载安装</li>\n<li>打开PowerShell，运行：ollama pull qwen:7b</li>\n<li>等它下载完（大约4GB）</li>\n<li>下载完运行：ollama run qwen:7b，能对话就说明安装成功</li>\n</ol>\n<p>回到Dify界面：设置→模型供应商→添加Ollama→填 http://host.docker.internal:11434 作为API地址。</p>\n<h3>常见问题排查</h3>\n<table border="1" cellpadding="10" cellspacing="0" style="border-collapse:collapse;width:100%">\n<tr style="background:#f5f5f5"><th>问题</th><th>原因</th><th>解决办法</th></tr>\n<tr><td>Docker启动失败</td><td>没开虚拟化</td><td>进BIOS开启VT-x</td></tr>\n<tr><td>内存不够</td><td>模型太大</td><td>换更小的模型，如qwen:4b</td></tr>\n<tr><td>Dify连不上Ollama</td><td>网络配置</td><td>API地址用host.docker.internal</td></tr>\n</table>\n<div style="background:#cce5ff;padding:15px;border-left:4px solid #004085;margin:20px 0">\n<strong>进阶提示</strong>：跑通基础版之后，你可以尝试给Agent添加知识库、接入联网搜索能力、用Dify的工作流功能实现多步骤自动化。\n</div>')

print("Article 10 deployed!")

# Article 11
update_post(11, '<h2>Windows电脑搭建AI Agent：零基础手把手教程</h2>\n<p>你不需要Mac，不需要Linux，不需要花几千块买新电脑。就用你手头那台Windows笔记本，跟着这篇文章一步步来，半小时内跑通你的第一个AI Agent。</p>\n<h3>先检查你的电脑够不够格</h3>\n<p>按 Win + Pause（或右键"此电脑"→属性），看内存≥8GB，系统Windows 10以上。</p>\n<h3>方案一：最快路径——用Coze（5分钟搞定）</h3>\n<p>如果你只想"立刻有个Agent能用"，这是最快的路：</p>\n<ol>\n<li>打开浏览器，访问 coze.cn</li>\n<li>用手机号注册（30秒）</li>\n<li>点击右上角"创建Bot"</li>\n<li>填写名称和描述</li>\n<li>点"生成"→"发布"→"开始对话"</li>\n</ol>\n<p>全程不到5分钟，你的第一个Agent就活了。试试给它发条消息："帮我写一封请假邮件"。</p>\n<h3>方案二：进阶路径——Ollama + Open WebUI（30分钟）</h3>\n<p>如果你想要更强的隐私保护和离线能力：</p>\n<ol>\n<li>访问 ollama.com/download 下载安装</li>\n<li>打开cmd，输入：ollama pull qwen2.5:7b</li>\n<li>等下载完（约4.7GB），安装Docker Desktop</li>\n<li>运行：docker run -d -p 3000:8080 --add-host=host.docker.internal:host-gateway -v open-webui:/app/backend/data --name open-webui --restart always ghcr.io/open-webui/open-webui:main</li>\n</ol>\n<p>访问 http://localhost:3000，注册管理员账号即可。</p>\n<h3>两种方案怎么选？</h3>\n<table border="1" cellpadding="10" cellspacing="0" style="border-collapse:collapse;width:100%">\n<tr style="background:#f5f5f5"><th>对比项</th><th>方案一：Coze</th><th>方案二：本地部署</th></tr>\n<tr><td>上手时间</td><td>5分钟</td><td>30分钟</td></tr>\n<tr><td>数据隐私</td><td>数据在云端</td><td>数据在本地</td></tr>\n<tr><td>免费程度</td><td>完全免费</td><td>完全免费</td></tr>\n</table>\n<div style="background:#d4edda;padding:15px;border-left:4px solid #28a745;margin:20px 0">\n<strong>我的建议</strong>：先用Coze体验5分钟，感受到Agent的威力之后，再花30分钟折腾本地部署。\n</div>')

print("Article 11 deployed!")

# Article 12
update_post(12, '<h2>阿里云/腾讯云部署AI Agent：从买服务器到上线全流程</h2>\n<p>本地跑Agent挺爽的，但有个问题——你得开着电脑才能用。想让它7×24小时在线，就得部署到云服务器上。我帮朋友部署过不下10台服务器上的Agent，今天把完整流程分享出来。</p>\n<h3>你需要花多少钱？</h3>\n<table border="1" cellpadding="10" cellspacing="0" style="border-collapse:collapse;width:100%">\n<tr style="background:#f5f5f5"><th>配置</th><th>规格</th><th>月费参考</th></tr>\n<tr><td>入门款</td><td>2核4G</td><td>约30-50元/月</td></tr>\n<tr><td>推荐款</td><td>2核8G</td><td>约60-90元/月</td></tr>\n</table>\n<h3>第一步：购买服务器</h3>\n<p>以阿里云为例：注册→实名认证→搜索"轻量应用服务器"→选择2核4G→Ubuntu 22.04→付款。</p>\n<h3>第二步：连接服务器</h3>\n<pre><code>ssh root@你的服务器IP</code></pre>\n<h3>第三步：安装Docker</h3>\n<pre><code>apt update &amp;&amp; apt upgrade -y\ncurl -fsSL https://get.docker.com | sh\nsystemctl start docker\nsystemctl enable docker</code></pre>\n<h3>第四步：一键部署Dify</h3>\n<pre><code>git clone https://github.com/langgenius/dify.git\ncd dify/docker\ncp .env.example .env\ndocker compose up -d</code></pre>\n<p>访问 http://服务器IP:5001 看到安装引导界面就成功了。</p>\n<h3>第六步：接入大模型API</h3>\n<p>推荐：通义千问（阿里云百炼）、DeepSeek（性价比最高）、文心一言（百度千帆）。在Dify的模型设置里添加对应的API Key。</p>\n<div style="background:#cce5ff;padding:15px;border-left:4px solid #004085;margin:20px 0">\n<strong>安全提醒</strong>：部署到公网后，务必修改默认SECRET_KEY、设置强密码、配置防火墙只开放80/443端口、定期更新Docker镜像。\n</div>')

print("Article 12 deployed!")
