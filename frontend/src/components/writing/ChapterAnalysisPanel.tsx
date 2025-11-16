import React, { useState, useEffect } from 'react'
import { Modal, Slider, Checkbox, Button, message, Spin } from 'antd'
import MarkdownRenderer from '@/components/MarkdownRenderer'
import chapterAnalysisService, { type ChapterAnalysis as ChapterAnalysisData } from '@/services/chapterAnalysisService'
import './ChapterAnalysisPanel.css'

export interface ChapterAnalysisProps {
  visible: boolean
  onClose: () => void
  novelId: number
  totalChapters: number
  onAnalyze: (params: AnalysisParams) => Promise<void>
}

export interface AnalysisParams {
  analysisTypes: string[]
  startChapter: number
  endChapter: number
}

export interface AnalysisResult {
  type: string
  typeName: string
  content: string
  createdAt: string
}

const ANALYSIS_TYPES = [
  { key: 'golden_three', name: '黄金三章', description: '黄金三章分析', icon: '⭐' },
  { key: 'main_plot', name: '主线剧情', description: '核心故事线发展', icon: '📖' },
  { key: 'sub_plot', name: '支线剧情', description: '辅助故事线分析', icon: '🌿' },
  { key: 'theme', name: '主题分析', description: '深层主题与意义', icon: '💡' },
  { key: 'character', name: '角色分析', description: '人物塑造与发展', icon: '👤' },
  { key: 'worldbuilding', name: '世界设定', description: '背景环境与规则', icon: '🌍' },
  { key: 'writing_style', name: '写作风格与技巧', description: '文笔风格和叙事技法', icon: '✍️' },
]

const ChapterAnalysisPanel: React.FC<ChapterAnalysisProps> = ({
  visible,
  onClose,
  novelId,
  totalChapters,
  onAnalyze,
}) => {
  const [chapterRange, setChapterRange] = useState<[number, number]>([1, Math.min(2, totalChapters)])
  const [selectedTypes, setSelectedTypes] = useState<string[]>([])
  const [analyzing, setAnalyzing] = useState(false)
  const [currentAnalysisType, setCurrentAnalysisType] = useState<string | null>(null)
  const [analysisResults, setAnalysisResults] = useState<Map<string, AnalysisResult>>(new Map())
  const [showResults, setShowResults] = useState(false)
  const [selectedResultType, setSelectedResultType] = useState<string | null>(null)
  const [loadingExisting, setLoadingExisting] = useState(false)

  // 加载已有的分析记录
  useEffect(() => {
    if (visible && novelId) {
      loadExistingAnalyses()
    }
  }, [visible, novelId])

  const loadExistingAnalyses = async () => {
    setLoadingExisting(true)
    try {
      const analyses = await chapterAnalysisService.getAnalysesByNovelId(novelId)
      const resultsMap = new Map<string, AnalysisResult>()
      
      analyses.forEach((analysis: ChapterAnalysisData) => {
        const typeInfo = ANALYSIS_TYPES.find(t => t.key === analysis.analysisType)
        if (typeInfo && 
            analysis.startChapter === chapterRange[0] && 
            analysis.endChapter === chapterRange[1]) {
          resultsMap.set(analysis.analysisType, {
            type: analysis.analysisType,
            typeName: typeInfo.name,
            content: analysis.analysisContent,
            createdAt: analysis.createdAt,
          })
        }
      })
      
      if (resultsMap.size > 0) {
        setAnalysisResults(resultsMap)
        setShowResults(true)
      }
    } catch (error: any) {
      console.error('加载分析记录失败:', error)
    } finally {
      setLoadingExisting(false)
    }
  }

  const handleSelectAll = () => {
    if (selectedTypes.length === ANALYSIS_TYPES.length) {
      setSelectedTypes([])
    } else {
      setSelectedTypes(ANALYSIS_TYPES.map(t => t.key))
    }
  }

  const handleClearAll = () => {
    setSelectedTypes([])
  }

  const handleToggleType = (typeKey: string) => {
    if (selectedTypes.includes(typeKey)) {
      setSelectedTypes(selectedTypes.filter(t => t !== typeKey))
    } else {
      setSelectedTypes([...selectedTypes, typeKey])
    }
  }

  const handleStartAnalysis = async () => {
    if (selectedTypes.length === 0) {
      message.warning('请至少选择一项分析内容')
      return
    }

    setAnalyzing(true)
    setShowResults(true)
    const results = new Map<string, AnalysisResult>(analysisResults)

    for (const typeKey of selectedTypes) {
      const typeInfo = ANALYSIS_TYPES.find(t => t.key === typeKey)
      if (!typeInfo) continue

      setCurrentAnalysisType(typeInfo.name)

      try {
        // 调用后端API进行分析
        const analysis = await chapterAnalysisService.createAnalysis(novelId, {
          analysisTypes: [typeKey],
          startChapter: chapterRange[0],
          endChapter: chapterRange[1],
        })
        
        results.set(typeKey, {
          type: typeKey,
          typeName: typeInfo.name,
          content: analysis.analysisContent,
          createdAt: analysis.createdAt,
        })
        setAnalysisResults(new Map(results))
      } catch (error: any) {
        message.error(`${typeInfo.name}分析失败: ${error.message || error}`)
      }
    }

    setAnalyzing(false)
    setCurrentAnalysisType(null)
    message.success('章节拆解分析完成')
  }

  const handleResultClick = (typeKey: string) => {
    setSelectedResultType(typeKey)
  }

  const selectedResult = selectedResultType ? analysisResults.get(selectedResultType) : null

  return (
    <>
      {/* 拆解设置弹窗 */}
      <Modal
        title="✂️ 小说章节拆解设置"
        open={visible && !showResults}
        onCancel={onClose}
        footer={null}
        width={480}
        centered
      >
        <div className="analysis-config">
          <div className="config-section">
            <div className="config-title">
              建议拆解100万字以内，字数越多，分析越耗时。
            </div>
            <div className="config-subtitle">
              每个准度值10万字约需要5000字
            </div>
          </div>

          <div className="config-section">
            <div className="config-label">章节拆解范围</div>
            <div className="chapter-range-selector">
              <div className="range-display">
                选择范围：第 {chapterRange[0]} 章 - 第 {chapterRange[1]} 章 (总计 {chapterRange[1] - chapterRange[0] + 1} 章)
              </div>
              <Slider
                range
                min={1}
                max={totalChapters}
                value={chapterRange}
                onChange={(value) => setChapterRange(value as [number, number])}
                marks={{
                  1: '1',
                  [totalChapters]: String(totalChapters),
                }}
              />
            </div>
          </div>

          <div className="config-section">
            <div className="config-label-row">
              <span className="config-label">拆解内容选择</span>
              <div className="selection-actions">
                <button className="action-link" onClick={handleSelectAll}>全选</button>
                <button className="action-link" onClick={handleClearAll}>清空</button>
              </div>
            </div>

            <div className="analysis-types-grid">
              {ANALYSIS_TYPES.map((type) => (
                <div
                  key={type.key}
                  className={`analysis-type-item ${selectedTypes.includes(type.key) ? 'selected' : ''}`}
                  onClick={() => handleToggleType(type.key)}
                >
                  <Checkbox checked={selectedTypes.includes(type.key)} />
                  <div className="type-icon">{type.icon}</div>
                  <div className="type-content">
                    <div className="type-name">{type.name}</div>
                    <div className="type-description">{type.description}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="config-footer">
            <Button onClick={onClose}>取消</Button>
            <Button
              type="primary"
              onClick={handleStartAnalysis}
              disabled={selectedTypes.length === 0}
              loading={analyzing}
            >
              开始拆解
            </Button>
          </div>
        </div>
      </Modal>

      {/* 分析结果弹窗 */}
      <Modal
        title={
          <div className="result-modal-title">
            ✂️ {selectedResult ? selectedResult.typeName : '章节拆解分析'}
            <button 
              className="close-result-btn"
              onClick={() => {
                setShowResults(false)
                setSelectedResultType(null)
                setAnalysisResults(new Map())
                onClose()
              }}
            >
              ✕
            </button>
          </div>
        }
        open={visible && showResults}
        onCancel={() => setShowResults(false)}
        footer={null}
        width={selectedResult ? 900 : 480}
        centered
        closable={false}
        maskClosable={false}
      >
        <div className="analysis-results">
          {analyzing ? (
            <div className="analyzing-state">
              <Spin size="large" />
              <div className="analyzing-text">
                正在分析：{currentAnalysisType}
              </div>
            </div>
          ) : analysisResults.size === 0 ? (
            <div className="empty-results">
              <div className="empty-icon">✂️</div>
              <div className="empty-title">暂无黄金三章数据</div>
              <div className="empty-subtitle">请先进行黄金三章分析以查看相关信息</div>
            </div>
          ) : selectedResult ? (
            <div className="result-detail-view">
              <Button 
                className="back-to-list-btn"
                onClick={() => setSelectedResultType(null)}
                style={{ marginBottom: '16px' }}
              >
                ← 返回列表
              </Button>
              <div className="result-content">
                <MarkdownRenderer content={selectedResult.content} compact={true} />
              </div>
            </div>
          ) : (
            <div className="results-list">
              {Array.from(analysisResults.values()).map((result) => (
                <div
                  key={result.type}
                  className="result-item"
                  onClick={() => handleResultClick(result.type)}
                >
                  <div className="result-icon">
                    {ANALYSIS_TYPES.find(t => t.key === result.type)?.icon}
                  </div>
                  <div className="result-info">
                    <div className="result-name">{result.typeName}</div>
                    <div className="result-preview">
                      {result.content.substring(0, 100)}...
                    </div>
                  </div>
                  <div className="result-arrow">→</div>
                </div>
              ))}
            </div>
          )}
        </div>
      </Modal>
    </>
  )
}

export default ChapterAnalysisPanel

