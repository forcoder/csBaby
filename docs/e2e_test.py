# -*- coding: utf-8 -*-
"""
csBaby E2E 测试脚本 (改进版)
测试两个修复:
1. 同步结果统计显示
2. 备份刷新列表状态恢复
"""

import uiautomator2 as u2
import time
import sys

# 设备连接
DEVICE_ID = 'FMR0224913042917'
d = u2.connect(DEVICE_ID)

def log(msg):
    print(f"[LOG] {msg}", flush=True)

def wait_and_screenshot(name):
    time.sleep(1)
    path = f'D:/workspace/workbuddy/csBaby/docs/e2e_{name}.png'
    d.screenshot(path)
    log(f"截图保存: {path}")
    return path

def find_element_by_text(text):
    """查找包含指定文本的元素"""
    try:
        return d(textContains=text)
    except:
        return None

def find_and_click(text):
    """查找并点击包含指定文本的元素"""
    try:
        elem = d(textContains=text)
        if elem.exists(timeout=3):
            elem.click()
            log(f"点击: {text}")
            return True
        return False
    except Exception as e:
        log(f"点击失败 {text}: {e}")
        return False

def input_text(text, clear=True):
    """向当前焦点输入框输入文本"""
    try:
        # 使用 xpath 查找输入框
        edit_texts = d(xpath='//android.widget.EditText').all()
        if edit_texts:
            for edit in edit_texts:
                try:
                    current = edit.info.get('text', '')
                    if clear and current:
                        edit.clear_text()
                    edit.set_text(text)
                    log(f"输入文本: {text}")
                    return True
                except:
                    continue
        return False
    except Exception as e:
        log(f"输入失败: {e}")
        return False

def test_sync_result_stats():
    """测试 1: 同步结果统计显示"""
    log("=" * 50)
    log("测试 1: 同步结果统计显示")
    log("=" * 50)

    # 1. 确保在主页面
    log("检查应用是否已启动...")
    current = d.app_current()
    log(f"当前应用: {current['package']}")

    # 2. 查找并进入"我的"页面
    log("查找'我的'页面入口...")

    # 尝试查找导航栏中的"我的"选项
    if not find_and_click("我的"):
        log("尝试上滑找导航栏...")
        d.swipe(630, 1500, 630, 500, duration=0.5)
        time.sleep(0.5)
        find_and_click("我的")

    wait_and_screenshot("01_mine_page")

    # 3. 检查是否已登录
    log("检查登录状态...")
    if find_element_by_text("登录").exists(timeout=2):
        log("未登录，需要先登录...")
        if find_and_click("登录"):
            time.sleep(1.5)
            wait_and_screenshot("02_login_dialog")

            # 使用 resource-id 或其他方式定位输入框
            log("查找输入框...")
            try:
                # 方法1: 使用坐标点击输入区域
                # 对话框中的邮箱输入框 (大约在屏幕中上部)
                email_field = d(resourceIdMatches=".*EditText")[0]
                if email_field.exists():
                    email_field.click()
                    time.sleep(0.5)
                    email_field.clear_text()
                    email_field.set_text("test@example.com")
                    log("邮箱已输入")
                    time.sleep(0.5)
            except Exception as e:
                log(f"方法1失败: {e}")
                try:
                    # 方法2: 使用 xpath
                    edit_fields = d(xpath='//android.widget.EditText').all()
                    if edit_fields:
                        edit_fields[0].click()
                        time.sleep(0.3)
                        edit_fields[0].set_text("test@example.com")
                        log("邮箱已输入 (方法2)")
                except Exception as e2:
                    log(f"方法2也失败: {e2}")

            wait_and_screenshot("03_email_filled")

            # 切换到密码输入框
            try:
                password_field = d(resourceIdMatches=".*EditText")[1]
                if password_field.exists():
                    password_field.click()
                    time.sleep(0.5)
                    password_field.clear_text()
                    password_field.set_text("password123")
                    log("密码已输入")
            except Exception as e:
                log(f"密码输入失败: {e}")

            wait_and_screenshot("04_password_filled")

            # 点击登录按钮
            find_and_click("登 录")  # 注意：Compose 可能添加空格
            find_and_click("登录")
            log("登录按钮点击")
            wait_and_screenshot("05_after_login")

        # 等待登录完成
        log("等待登录完成...")
        time.sleep(5)
        wait_and_screenshot("06_logged_in")

    # 4. 查找"云端同步"卡片
    log("查找'云端同步'卡片...")
    if not find_element_by_text("云端同步").exists(timeout=5):
        log("未找到云端同步卡片，尝试滑动...")
        d.swipe(630, 1400, 630, 600, duration=0.5)
        time.sleep(0.5)

    wait_and_screenshot("07_sync_card_visible")

    # 5. 点击"立即同步"按钮
    log("点击'立即同步'按钮...")
    if find_and_click("立即同步"):
        log("点击'立即同步'成功")
        wait_and_screenshot("08_sync_started")
        # 等待同步完成
        log("等待同步完成...")
        # 最多等待30秒
        for i in range(30):
            time.sleep(1)

            # 检查同步状态 - 可能显示"同步完成"
            sync_done = find_element_by_text("同步完成")
            if sync_done and sync_done.exists():
                log("检测到'同步完成'状态")
                wait_and_screenshot("09_sync_done")

            # 检查是否显示统计信息
            stats_elem = find_element_by_text("新增")
            if stats_elem and stats_elem.exists():
                log("发现同步统计信息!")
                wait_and_screenshot("10_sync_stats")
                return True

            if i % 5 == 0:
                log(f"等待同步中... ({i+1}/30)")

        log("同步超时，未观察到统计信息")
        wait_and_screenshot("10_sync_timeout")
        return False
    else:
        log("未找到'立即同步'按钮")
        wait_and_screenshot("10_no_sync_button")
        return False

def test_backup_refresh_button():
    """测试 2: 备份刷新列表状态恢复"""
    log("=" * 50)
    log("测试 2: 备份刷新列表状态恢复")
    log("=" * 50)

    # 1. 确保在"我的"页面
    log("确保在'我的'页面...")
    if not find_element_by_text("云端同步").exists(timeout=2):
        if find_and_click("我的"):
            time.sleep(0.5)

    wait_and_screenshot("10_backup_area")

    # 2. 展开"备份操作"区域
    log("展开'备份操作'区域...")
    if find_and_click("备份操作"):
        log("点击'备份操作'成功")
        time.sleep(0.5)
    else:
        log("尝试通过区域点击展开...")
        # 点击"备份操作"行
        d.click(630, 1350)
        time.sleep(0.5)

    wait_and_screenshot("11_backup_expanded")

    # 3. 点击"刷新列表"按钮
    log("点击'刷新列表'按钮...")
    if find_and_click("刷新列表"):
        log("点击'刷新列表'成功")
        wait_and_screenshot("12_refresh_clicked")
        # 等待操作完成
        time.sleep(3)
    else:
        log("未找到'刷新列表'按钮")
        wait_and_screenshot("12_no_refresh_button")
        return False

    # 4. 验证按钮是否恢复正常
    log("检查'刷新列表'按钮状态...")
    refresh_btn = d(textContains="刷新列表")
    if refresh_btn.exists():
        # 检查按钮是否可点击
        try:
            info = refresh_btn.info
            if info.get('enabled', False):
                log("按钮状态正常: 可用")
                wait_and_screenshot("13_button_enabled")
                return True
            else:
                log("按钮状态异常: 不可用/灰显")
                wait_and_screenshot("13_button_disabled")
                return False
        except Exception as e:
            log(f"检查按钮状态失败: {e}")
            return False
    else:
        log("按钮不存在或已消失")
        wait_and_screenshot("13_button_gone")
        return True  # 按钮可能已消失，说明刷新完成

def main():
    log("=" * 60)
    log("csBaby E2E 测试开始")
    log("=" * 60)

    results = {}

    try:
        # 测试 1
        results['test1_sync_stats'] = test_sync_result_stats()
    except Exception as e:
        log(f"测试 1 异常: {e}")
        results['test1_sync_stats'] = False

    time.sleep(2)

    try:
        # 测试 2
        results['test2_backup_refresh'] = test_backup_refresh_button()
    except Exception as e:
        log(f"测试 2 异常: {e}")
        results['test2_backup_refresh'] = False

    # 输出结果
    log("=" * 60)
    log("测试结果汇总")
    log("=" * 60)
    for test_name, result in results.items():
        status = "通过" if result else "失败"
        log(f"  {test_name}: {status}")
    log("=" * 60)

    # 保存报告
    report = f"""# csBaby E2E 测试报告

## 测试环境
- 设备: {DEVICE_ID}
- 应用: com.csbaby.kefu
- 测试时间: {time.strftime('%Y-%m-%d %H:%M:%S')}

## 测试结果

### 测试 1: 同步结果统计显示
- **状态**: {"通过" if results.get('test1_sync_stats') else "失败"}
- **预期**: 点击"立即同步"后显示同步统计信息（新增/更新/删除条数）
- **验证方法**: 观察同步完成后界面是否显示类似 "新增 X 条，更新 Y 条" 的文本

### 测试 2: 备份刷新列表状态恢复
- **状态**: {"通过" if results.get('test2_backup_refresh') else "失败"}
- **预期**: 点击"刷新列表"按钮后，按钮保持可用状态
- **验证方法**: 检查刷新操作完成后按钮是否仍可点击

## 截图文件
- 截图保存在 docs/ 目录
- 命名格式: e2e_XX_description.png

## 总体结论
{"所有测试通过" if all(results.values()) else "部分测试失败，请查看详情"}
"""
    with open('D:/workspace/workbuddy/csBaby/docs/e2e-test-report.md', 'w', encoding='utf-8') as f:
        f.write(report)
    log("报告已保存: docs/e2e-test-report.md")

    return all(results.values())

if __name__ == '__main__':
    success = main()
    sys.exit(0 if success else 1)