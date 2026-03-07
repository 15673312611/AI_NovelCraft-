/**
 * 提示词导入脚本
 * 将 prompts_output 目录下的提示词文件导入到数据库
 * 
 * 使用方法：
 * 1. 确保 MySQL 连接配置正确
 * 2. 运行: node import_prompts.js
 */

const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');

// 数据库配置 - 请根据实际情况修改
const dbConfig = {
  host: 'localhost',
  port: 3306,
  user: 'root',
  password: 'root',
  database: 'ai_novel',
  charset: 'utf8mb4'
};

// 提示词目录
const PROMPTS_DIR = path.join(__dirname, '../prompts_output');

// 分类映射 - 根据文件名关键词自动分类
const categoryMapping = {
  // 写作正文类
  '写作指令': 'writing_content',
  '正文': 'writing_content',
  '创作指令': 'writing_content',
  '爽文': 'writing_content',
  '叙事': 'writing_content',
  '文风': 'writing_style',
  '风格': 'writing_style',
  
  // 大纲类
  '大纲': 'outline',
  '细纲': 'outline',
  '蓝图': 'outline',
  '剧情规划': 'outline',
  '事件流': 'outline',
  
  // 角色类
  '角色': 'character',
  '人物': 'character',
  
  // 设定类
  '设定': 'worldbuilding',
  '世界': 'worldbuilding',
  
  // 工具类
  '生成器': 'tool',
  '诊断': 'tool',
  '修复': 'tool',
  '检测': 'tool',
  '审稿': 'tool',
  '润色': 'tool',
  '消痕': 'anti_ai',
  'AI率': 'anti_ai',
  
  // 脑洞/创意类
  '脑洞': 'brainstorm',
  '创意': 'brainstorm',
  
  // 其他
  '书名': 'title_synopsis',
  '简介': 'title_synopsis',
  '封面': 'cover',
};

// 根据文件名判断分类
function getCategory(filename) {
  for (const [keyword, category] of Object.entries(categoryMapping)) {
    if (filename.includes(keyword)) {
      return category;
    }
  }
  return 'other'; // 默认分类
}

// 清理文件名作为模板名称
function getTemplateName(filename) {
  return filename
    .replace('.txt', '')
    .replace(/_/g, ' ')
    .trim();
}

// 转义 SQL 字符串
function escapeString(str) {
  if (!str) return '';
  return str
    .replace(/\\/g, '\\\\')
    .replace(/'/g, "\\'")
    .replace(/"/g, '\\"')
    .replace(/\n/g, '\\n')
    .replace(/\r/g, '\\r')
    .replace(/\t/g, '\\t');
}

async function importPrompts() {
  let connection;
  
  try {
    // 连接数据库
    console.log('正在连接数据库...');
    connection = await mysql.createConnection(dbConfig);
    console.log('数据库连接成功！\n');

    // 读取目录下所有 txt 文件
    const files = fs.readdirSync(PROMPTS_DIR)
      .filter(f => f.endsWith('.txt'));
    
    console.log(`找到 ${files.length} 个提示词文件\n`);

    let successCount = 0;
    let skipCount = 0;
    let errorCount = 0;

    for (const file of files) {
      const filePath = path.join(PROMPTS_DIR, file);
      const content = fs.readFileSync(filePath, 'utf-8');
      const name = getTemplateName(file);
      const category = getCategory(file);
      
      // 检查是否已存在
      const [existing] = await connection.execute(
        'SELECT id FROM prompt_templates WHERE name = ?',
        [name]
      );
      
      if (existing.length > 0) {
        console.log(`⏭️  跳过（已存在）: ${name}`);
        skipCount++;
        continue;
      }

      // 生成描述（取内容前100个字符）
      const description = content.substring(0, 100).replace(/\n/g, ' ').trim() + '...';

      try {
        await connection.execute(
          `INSERT INTO prompt_templates 
           (name, content, type, category, description, is_active, is_default, usage_count) 
           VALUES (?, ?, 'official', ?, ?, 1, 0, 0)`,
          [name, content, category, description]
        );
        console.log(`✅ 导入成功: ${name} [${category}]`);
        successCount++;
      } catch (err) {
        console.error(`❌ 导入失败: ${name} - ${err.message}`);
        errorCount++;
      }
    }

    console.log('\n========== 导入完成 ==========');
    console.log(`成功: ${successCount}`);
    console.log(`跳过: ${skipCount}`);
    console.log(`失败: ${errorCount}`);
    console.log(`总计: ${files.length}`);

  } catch (error) {
    console.error('导入过程出错:', error);
  } finally {
    if (connection) {
      await connection.end();
      console.log('\n数据库连接已关闭');
    }
  }
}

// 生成 SQL 文件（备选方案，不需要连接数据库）
function generateSQLFile() {
  const files = fs.readdirSync(PROMPTS_DIR)
    .filter(f => f.endsWith('.txt'));
  
  let sql = '-- 提示词模板导入 SQL\n';
  sql += '-- 生成时间: ' + new Date().toISOString() + '\n\n';

  for (const file of files) {
    const filePath = path.join(PROMPTS_DIR, file);
    const content = fs.readFileSync(filePath, 'utf-8');
    const name = getTemplateName(file);
    const category = getCategory(file);
    const description = content.substring(0, 100).replace(/\n/g, ' ').trim() + '...';

    sql += `-- ${name}\n`;
    sql += `INSERT INTO prompt_templates (name, content, type, category, description, is_active, is_default, usage_count) VALUES (\n`;
    sql += `  '${escapeString(name)}',\n`;
    sql += `  '${escapeString(content)}',\n`;
    sql += `  'official',\n`;
    sql += `  '${category}',\n`;
    sql += `  '${escapeString(description)}',\n`;
    sql += `  1, 0, 0\n`;
    sql += `) ON DUPLICATE KEY UPDATE content = VALUES(content), category = VALUES(category), description = VALUES(description);\n\n`;
  }

  const outputPath = path.join(__dirname, '../sql/import_prompts.sql');
  fs.writeFileSync(outputPath, sql, 'utf-8');
  console.log(`SQL 文件已生成: ${outputPath}`);
  console.log(`共 ${files.length} 条记录`);
}

// 根据命令行参数选择执行方式
const args = process.argv.slice(2);
if (args.includes('--sql')) {
  generateSQLFile();
} else {
  importPrompts();
}
