/**
 * 智能章纲生成服务
 * 
 * 核心设计思路：
 * 1. 分批迭代生成 - 不一次性生成30章，而是分成多个"情节单元"（每单元5-7章）
 * 2. 看点驱动 - 先提取/生成"必须包含的看点"，再围绕看点编排章节
 * 3. 质量检测 - 自动检测"平庸模式"并强制优化
 * 
 * 工作流程：
 * Step 1: 看点提取 - 从大纲/卷蓝图中提取核心冲突、转折点、高潮
 * Step 2: 节奏模板匹配 - 选择叙事节奏模板，将章节划分为情节单元
 * Step 3: 分批迭代生成 - 每次只生成1个情节单元
 * Step 4: 质量检查 & 自动优化 - 检测平庸模式，强制注入冲突/悬念/反转
 */

import api from './api'
import { withAIConfig } from '../utils/aiRequest'

// ============ 类型定义 ============

/** 看点类型 */
export type HighlightType = 
  | 'CONFLICT'      // 冲突
  | 'REVERSAL'      // 反转
  | 'CLIMAX'        // 高潮
  | 'SUSPENSE'      // 悬念
  | 'PAYOFF'        // 伏笔回收
  | 'EMOTION'       // 情感爆发
  | 'FACE_SLAP'     // 打脸
  | 'POWER_UP'      // 升级/突破

/** 看点条目 */
export interface Highlight {
  id: string
  type: HighlightType
  description: string
  targetChapter?: number  // 建议出现的章节
  priority: 'HIGH' | 'MEDIUM' | 'LOW'
  source: 'OUTLINE' | 'VOLUME' | 'AI_GENERATED' | 'USER'
}

/** 情节单元 */
export interface PlotUnit {
  id: string
  name: string
  startChapter: number
  endChapter: number
  theme: string
  requiredHighlights: string[]  // 必须包含的看点ID
  status: 'PENDING' | 'GENERATING' | 'COMPLETED' | 'FAILED'
  generatedOutlines?: ChapterOutlineItem[]
}

/** 章节大纲条目 */
export interface ChapterOutlineItem {
  chapterInVolume: number
  globalChapterNumber?: number
  direction: string
  keyPlotPoints: string[]
  emotionalTone: string
  foreshadowAction?: string
  foreshadowDetail?: string
  subplot?: string
  antagonism?: {
    opponent?: string
    stakes?: string
    method?: string
  }
  hookEnding?: string  // 章末钩子
  qualityScore?: number  // 质量评分 0-100
  qualityIssues?: string[]  // 质量问题
}

/** 节奏模板 */
export interface RhythmTemplate {
  id: string
  name: string
  description: string
  pattern: {
    unitName: string
    chapterCount: number
    intensity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CLIMAX'
    requiredElements: string[]
  }[]
}

/** 生成配置 */
export interface SmartGenerationConfig {
  volumeId: number
  novelId: number
  totalChapters: number
  rhythmTemplateId?: string
  customHighlights?: Highlight[]
  qualityThreshold?: number  // 质量阈值，低于此分数自动优化
  maxRetries?: number  // 单个单元最大重试次数
}

/** 生成进度 */
export interface GenerationProgress {
  phase: 'EXTRACTING_HIGHLIGHTS' | 'PLANNING_UNITS' | 'GENERATING_UNIT' | 'QUALITY_CHECK' | 'OPTIMIZING' | 'COMPLETED' | 'FAILED'
  currentUnit?: number
  totalUnits?: number
  currentChapter?: number
  totalChapters?: number
  message: string
  percentage: number
  highlights?: Highlight[]
  plotUnits?: PlotUnit[]
  qualityReport?: QualityReport
}

/** 质量报告 */
export interface QualityReport {
  overallScore: number
  issues: {
    type: 'BORING_TRANSITION' | 'WEAK_HOOK' | 'NO_CONFLICT' | 'PREDICTABLE' | 'INFO_DUMP'
    chapterIndex: number
    description: string
    suggestion: string
  }[]
  strengths: string[]
}

// ============ 预设节奏模板 ============

// 固定35章的节奏模板
export const RHYTHM_TEMPLATES: RhythmTemplate[] = [
  {
    id: 'wave',
    name: '波浪式节奏',
    description: '张弛有度，高潮-缓冲-高潮交替，适合长篇连载',
    pattern: [
      { unitName: '开局破冰', chapterCount: 6, intensity: 'MEDIUM', requiredElements: ['CONFLICT', 'SUSPENSE'] },
      { unitName: '矛盾升级', chapterCount: 8, intensity: 'HIGH', requiredElements: ['CONFLICT', 'REVERSAL'] },
      { unitName: '短暂喘息', chapterCount: 5, intensity: 'LOW', requiredElements: ['EMOTION', 'PAYOFF'] },
      { unitName: '危机爆发', chapterCount: 9, intensity: 'CLIMAX', requiredElements: ['CLIMAX', 'FACE_SLAP'] },
      { unitName: '收尾铺垫', chapterCount: 7, intensity: 'MEDIUM', requiredElements: ['SUSPENSE', 'PAYOFF'] },
    ]
  },
  {
    id: 'escalation',
    name: '递进式节奏',
    description: '压力持续递增，适合爽文、逆袭流',
    pattern: [
      { unitName: '蛰伏期', chapterCount: 5, intensity: 'LOW', requiredElements: ['SUSPENSE'] },
      { unitName: '初露锋芒', chapterCount: 7, intensity: 'MEDIUM', requiredElements: ['CONFLICT', 'FACE_SLAP'] },
      { unitName: '强敌出现', chapterCount: 8, intensity: 'HIGH', requiredElements: ['CONFLICT', 'REVERSAL'] },
      { unitName: '绝地反击', chapterCount: 9, intensity: 'HIGH', requiredElements: ['CLIMAX', 'POWER_UP'] },
      { unitName: '碾压收割', chapterCount: 6, intensity: 'CLIMAX', requiredElements: ['FACE_SLAP', 'PAYOFF'] },
    ]
  },
  {
    id: 'suspense',
    name: '悬疑式节奏',
    description: '层层剥茧，真相逐步揭露，适合宫斗、权谋',
    pattern: [
      { unitName: '迷雾初现', chapterCount: 6, intensity: 'MEDIUM', requiredElements: ['SUSPENSE'] },
      { unitName: '线索交织', chapterCount: 8, intensity: 'MEDIUM', requiredElements: ['SUSPENSE', 'CONFLICT'] },
      { unitName: '假象破灭', chapterCount: 6, intensity: 'HIGH', requiredElements: ['REVERSAL'] },
      { unitName: '真相浮现', chapterCount: 8, intensity: 'HIGH', requiredElements: ['PAYOFF', 'EMOTION'] },
      { unitName: '终极对决', chapterCount: 7, intensity: 'CLIMAX', requiredElements: ['CLIMAX', 'FACE_SLAP'] },
    ]
  },
  {
    id: 'three_act',
    name: '三幕式经典',
    description: '起承转合，经典叙事结构',
    pattern: [
      { unitName: '第一幕：建置', chapterCount: 9, intensity: 'MEDIUM', requiredElements: ['CONFLICT', 'SUSPENSE'] },
      { unitName: '第二幕A：对抗', chapterCount: 12, intensity: 'HIGH', requiredElements: ['CONFLICT', 'REVERSAL', 'EMOTION'] },
      { unitName: '第二幕B：危机', chapterCount: 8, intensity: 'CLIMAX', requiredElements: ['CLIMAX', 'REVERSAL'] },
      { unitName: '第三幕：解决', chapterCount: 6, intensity: 'HIGH', requiredElements: ['PAYOFF', 'FACE_SLAP'] },
    ]
  }
]

// ============ 服务方法 ============

/**
 * 第一步：从大纲和卷蓝图中提取看点
 */
export const extractHighlights = async (
  novelId: number,
  volumeId: number
): Promise<Highlight[]> => {
  const res: any = await api.post(
    `/volumes/${volumeId}/smart-outline/extract-highlights`,
    withAIConfig({ novelId })
  )
  return res?.highlights || res?.data?.highlights || []
}

/**
 * 第二步：根据节奏模板规划情节单元
 */
export const planPlotUnits = async (
  volumeId: number,
  totalChapters: number,
  rhythmTemplateId: string,
  highlights: Highlight[]
): Promise<PlotUnit[]> => {
  const res: any = await api.post(
    `/volumes/${volumeId}/smart-outline/plan-units`,
    withAIConfig({
      totalChapters,
      rhythmTemplateId,
      highlights
    })
  )
  return res?.plotUnits || res?.data?.plotUnits || []
}

/**
 * 第三步：生成单个情节单元的章纲
 */
export const generatePlotUnitOutlines = async (
  volumeId: number,
  plotUnit: PlotUnit,
  previousOutlines: ChapterOutlineItem[],
  highlights: Highlight[]
): Promise<ChapterOutlineItem[]> => {
  const res: any = await api.post(
    `/volumes/${volumeId}/smart-outline/generate-unit`,
    withAIConfig({
      plotUnit,
      previousOutlines,
      highlights
    })
  )
  return res?.outlines || res?.data?.outlines || []
}

/**
 * 第四步：质量检查
 */
export const checkOutlineQuality = async (
  volumeId: number,
  outlines: ChapterOutlineItem[]
): Promise<QualityReport> => {
  const res: any = await api.post(
    `/volumes/${volumeId}/smart-outline/quality-check`,
    withAIConfig({ outlines })
  )
  return res?.report || res?.data?.report || { overallScore: 0, issues: [], strengths: [] }
}

/**
 * 第五步：优化低质量章纲
 */
export const optimizeOutlines = async (
  volumeId: number,
  outlines: ChapterOutlineItem[],
  issues: QualityReport['issues']
): Promise<ChapterOutlineItem[]> => {
  const res: any = await api.post(
    `/volumes/${volumeId}/smart-outline/optimize`,
    withAIConfig({ outlines, issues })
  )
  return res?.outlines || res?.data?.outlines || outlines
}

/**
 * 完整的智能章纲生成流程（流式）
 */
export const startSmartGeneration = async (
  config: SmartGenerationConfig,
  onProgress: (progress: GenerationProgress) => void
): Promise<ChapterOutlineItem[]> => {
  const { volumeId, novelId, totalChapters, rhythmTemplateId = 'wave', qualityThreshold = 60, maxRetries = 2 } = config

  let allOutlines: ChapterOutlineItem[] = []
  let highlights: Highlight[] = []
  let plotUnits: PlotUnit[] = []

  try {
    // Phase 1: 提取看点
    onProgress({
      phase: 'EXTRACTING_HIGHLIGHTS',
      message: '正在分析大纲，提取核心看点...',
      percentage: 5
    })

    console.log('🔍 开始提取看点, novelId:', novelId, 'volumeId:', volumeId)
    
    try {
      highlights = await extractHighlights(novelId, volumeId)
      console.log('✅ 提取看点结果:', highlights)
    } catch (extractError: any) {
      console.error('❌ 提取看点失败:', extractError)
      // 如果提取失败，使用默认看点继续
      highlights = generateDefaultHighlights(rhythmTemplateId)
      console.log('⚠️ 使用默认看点:', highlights)
    }
    
    if (config.customHighlights) {
      highlights = [...highlights, ...config.customHighlights]
    }
    
    // 如果看点为空，生成默认看点
    if (!highlights || highlights.length === 0) {
      console.log('⚠️ 看点为空，生成默认看点')
      highlights = generateDefaultHighlights(rhythmTemplateId)
    }

    onProgress({
      phase: 'EXTRACTING_HIGHLIGHTS',
      message: `已提取 ${highlights.length} 个看点`,
      percentage: 15,
      highlights
    })

    // Phase 2: 规划情节单元
    onProgress({
      phase: 'PLANNING_UNITS',
      message: '正在规划情节单元...',
      percentage: 20
    })

    console.log('📋 开始规划情节单元, totalChapters:', totalChapters, 'template:', rhythmTemplateId)
    
    try {
      plotUnits = await planPlotUnits(volumeId, totalChapters, rhythmTemplateId, highlights)
      console.log('✅ 规划情节单元结果:', plotUnits)
    } catch (planError: any) {
      console.error('❌ 规划情节单元失败:', planError)
      // 如果规划失败，使用本地模板生成
      plotUnits = generateLocalPlotUnits(rhythmTemplateId, totalChapters, highlights)
      console.log('⚠️ 使用本地模板生成情节单元:', plotUnits)
    }
    
    // 如果情节单元为空，使用本地模板
    if (!plotUnits || plotUnits.length === 0) {
      console.log('⚠️ 情节单元为空，使用本地模板')
      plotUnits = generateLocalPlotUnits(rhythmTemplateId, totalChapters, highlights)
    }

    onProgress({
      phase: 'PLANNING_UNITS',
      message: `已规划 ${plotUnits.length} 个情节单元`,
      percentage: 25,
      plotUnits
    })

    // Phase 3: 分批生成章纲
    const totalUnits = plotUnits.length
    console.log('📝 开始分批生成章纲, 共', totalUnits, '个单元')
    
    for (let i = 0; i < totalUnits; i++) {
      const unit = plotUnits[i]
      unit.status = 'GENERATING'

      onProgress({
        phase: 'GENERATING_UNIT',
        currentUnit: i + 1,
        totalUnits,
        message: `正在生成「${unit.name}」(${unit.startChapter}-${unit.endChapter}章)...`,
        percentage: 25 + (i / totalUnits) * 50,
        plotUnits
      })

      let unitOutlines: ChapterOutlineItem[] = []
      let retries = 0
      let qualityOk = false

      while (!qualityOk && retries < maxRetries) {
        console.log(`📝 生成单元 ${i + 1}/${totalUnits}: ${unit.name}, 重试次数: ${retries}`)
        
        try {
          // 生成当前单元
          unitOutlines = await generatePlotUnitOutlines(
            volumeId,
            unit,
            allOutlines,
            highlights.filter(h => unit.requiredHighlights.includes(h.id))
          )
          console.log(`✅ 单元 ${unit.name} 生成结果:`, unitOutlines)
        } catch (genError: any) {
          console.error(`❌ 单元 ${unit.name} 生成失败:`, genError)
          // 生成失败时，创建占位章纲
          unitOutlines = generatePlaceholderOutlines(unit)
          console.log(`⚠️ 使用占位章纲:`, unitOutlines)
        }
        
        // 如果生成结果为空，创建占位章纲
        if (!unitOutlines || unitOutlines.length === 0) {
          console.log(`⚠️ 单元 ${unit.name} 结果为空，创建占位章纲`)
          unitOutlines = generatePlaceholderOutlines(unit)
        }

        // 质量检查（可选，如果失败则跳过）
        try {
          onProgress({
            phase: 'QUALITY_CHECK',
            currentUnit: i + 1,
            totalUnits,
            message: `正在检查「${unit.name}」质量...`,
            percentage: 25 + ((i + 0.7) / totalUnits) * 50
          })

          const report = await checkOutlineQuality(volumeId, unitOutlines)
          console.log(`📊 质量检查结果:`, report)

          if (report.overallScore >= qualityThreshold) {
            qualityOk = true
            unit.status = 'COMPLETED'
          } else {
            retries++
            if (retries < maxRetries) {
              onProgress({
                phase: 'OPTIMIZING',
                currentUnit: i + 1,
                totalUnits,
                message: `质量评分 ${report.overallScore}，正在优化（第${retries}次）...`,
                percentage: 25 + ((i + 0.8) / totalUnits) * 50,
                qualityReport: report
              })

              try {
                unitOutlines = await optimizeOutlines(volumeId, unitOutlines, report.issues)
              } catch (optError) {
                console.error('优化失败，使用当前结果:', optError)
              }
            } else {
              // 达到最大重试次数，接受当前结果
              unit.status = 'COMPLETED'
              qualityOk = true
            }
          }
        } catch (qualityError) {
          console.error('质量检查失败，跳过:', qualityError)
          unit.status = 'COMPLETED'
          qualityOk = true
        }
      }

      unit.generatedOutlines = unitOutlines
      allOutlines = [...allOutlines, ...unitOutlines]
    }

    // Phase 4: 完成
    onProgress({
      phase: 'COMPLETED',
      message: `智能章纲生成完成！共 ${allOutlines.length} 章`,
      percentage: 100,
      highlights,
      plotUnits
    })

    return allOutlines

  } catch (error: any) {
    console.error('❌ 智能章纲生成失败:', error)
    onProgress({
      phase: 'FAILED',
      message: `生成失败: ${error.message}`,
      percentage: 0
    })
    throw error
  }
}

/**
 * 生成默认看点（当AI提取失败时使用）
 */
function generateDefaultHighlights(_templateId: string): Highlight[] {
  const highlights: Highlight[] = []
  
  const highlightTypes: HighlightType[] = ['CONFLICT', 'REVERSAL', 'CLIMAX', 'SUSPENSE', 'PAYOFF', 'FACE_SLAP']
  
  highlightTypes.forEach((type, index) => {
    highlights.push({
      id: `default_${index + 1}`,
      type,
      description: getDefaultHighlightDescription(type),
      priority: index < 3 ? 'HIGH' : 'MEDIUM',
      source: 'AI_GENERATED'
    })
  })
  
  return highlights
}

function getDefaultHighlightDescription(type: HighlightType): string {
  const descriptions: Record<HighlightType, string> = {
    'CONFLICT': '核心冲突爆发',
    'REVERSAL': '剧情反转',
    'CLIMAX': '高潮对决',
    'SUSPENSE': '悬念设置',
    'PAYOFF': '伏笔回收',
    'EMOTION': '情感爆发',
    'FACE_SLAP': '打脸爽点',
    'POWER_UP': '实力突破'
  }
  return descriptions[type] || '关键剧情点'
}

/**
 * 本地生成情节单元（当后端规划失败时使用）
 */
function generateLocalPlotUnits(templateId: string, totalChapters: number, highlights: Highlight[]): PlotUnit[] {
  const template = RHYTHM_TEMPLATES.find(t => t.id === templateId) || RHYTHM_TEMPLATES[0]
  const plotUnits: PlotUnit[] = []
  
  let currentChapter = 1
  const scale = totalChapters / template.pattern.reduce((sum, p) => sum + p.chapterCount, 0)
  
  template.pattern.forEach((pattern, index) => {
    const chapterCount = Math.max(1, Math.round(pattern.chapterCount * scale))
    const startChapter = currentChapter
    const endChapter = Math.min(totalChapters, currentChapter + chapterCount - 1)
    
    // 分配看点
    const requiredHighlights = highlights
      .filter(h => pattern.requiredElements.includes(h.type))
      .map(h => h.id)
    
    plotUnits.push({
      id: `unit_${index + 1}`,
      name: pattern.unitName,
      startChapter,
      endChapter,
      theme: pattern.unitName,
      requiredHighlights,
      status: 'PENDING'
    })
    
    currentChapter = endChapter + 1
  })
  
  return plotUnits
}

/**
 * 生成占位章纲（当AI生成失败时使用）
 */
function generatePlaceholderOutlines(unit: PlotUnit): ChapterOutlineItem[] {
  const outlines: ChapterOutlineItem[] = []
  
  for (let i = unit.startChapter; i <= unit.endChapter; i++) {
    outlines.push({
      chapterInVolume: i,
      direction: `【${unit.name}】第${i}章 - 待AI生成`,
      keyPlotPoints: ['待生成'],
      emotionalTone: unit.theme.includes('高潮') ? '紧张' : '中等',
      hookEnding: '待生成'
    })
  }
  
  return outlines
}

/**
 * 保存生成的章纲到数据库
 */
export const saveGeneratedOutlines = async (
  volumeId: number,
  outlines: ChapterOutlineItem[]
): Promise<void> => {
  await api.post(
    `/volumes/${volumeId}/smart-outline/save`,
    withAIConfig({ outlines })
  )
}

/**
 * 获取节奏模板列表
 */
export const getRhythmTemplates = (): RhythmTemplate[] => {
  return RHYTHM_TEMPLATES
}

/**
 * 根据ID获取节奏模板
 */
export const getRhythmTemplateById = (id: string): RhythmTemplate | undefined => {
  return RHYTHM_TEMPLATES.find(t => t.id === id)
}
