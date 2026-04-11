# 1. 确保在 dev 分支开发
git checkout dev
# 2. 开发代码...

# 3.提交
git add .
git commit -m "功能说明"
# 4. 合并回 lesson-init 并推送到两边
git checkout lesson-init
git merge dev
git push origin lesson-init  # 同时推送到 GitHub 和私服

# 基于 lesson-init 创建全新的 dev 分支
git checkout -b dev lesson-init

这是在dev中更新第三次
