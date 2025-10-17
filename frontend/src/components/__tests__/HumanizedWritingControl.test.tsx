import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import HumanizedWritingControl from '../HumanizedWritingControl';

describe('HumanizedWritingControl', () => {
  const defaultProps = {
    aiScore: 0.3,
    qualityLevel: '良好',
    optimizationApplied: false,
    aiFeatures: ['过于正式的表达', '缺乏口语化语言'],
    suggestions: ['增加更多生活化的表达', '使用更自然的语言'],
    onOptimizeRequest: jest.fn(),
    onRegenerate: jest.fn(),
    loading: false
  };

  it('should render correctly with default props', () => {
    render(<HumanizedWritingControl {...defaultProps} />);
    
    expect(screen.getByText('AI写作人性化控制')).toBeInTheDocument();
    expect(screen.getByText('人性化程度')).toBeInTheDocument();
    expect(screen.getByText('70%')).toBeInTheDocument(); // (1 - 0.3) * 100
    expect(screen.getByText('良好')).toBeInTheDocument();
  });

  it('should show optimization button when AI score is high', () => {
    const highScoreProps = {
      ...defaultProps,
      aiScore: 0.8 // AI痕迹较重
    };
    
    render(<HumanizedWritingControl {...highScoreProps} />);
    
    expect(screen.getByText('立即优化')).toBeInTheDocument();
  });

  it('should show optimization applied badge when optimized', () => {
    const optimizedProps = {
      ...defaultProps,
      optimizationApplied: true
    };
    
    render(<HumanizedWritingControl {...optimizedProps} />);
    
    expect(screen.getByText('已优化')).toBeInTheDocument();
    expect(screen.getByText('内容已人性化处理')).toBeInTheDocument();
  });

  it('should call onOptimizeRequest when optimize button is clicked', () => {
    const highScoreProps = {
      ...defaultProps,
      aiScore: 0.8,
      onOptimizeRequest: jest.fn()
    };
    
    render(<HumanizedWritingControl {...highScoreProps} />);
    
    const optimizeButton = screen.getByText('立即优化');
    fireEvent.click(optimizeButton);
    
    expect(highScoreProps.onOptimizeRequest).toHaveBeenCalled();
  });

  it('should show suggestions when provided', () => {
    render(<HumanizedWritingControl {...defaultProps} />);
    
    expect(screen.getByText('优化建议')).toBeInTheDocument();
    expect(screen.getByText('• 增加更多生活化的表达')).toBeInTheDocument();
    expect(screen.getByText('• 使用更自然的语言')).toBeInTheDocument();
  });

  it('should open details modal when detail button is clicked', () => {
    render(<HumanizedWritingControl {...defaultProps} />);
    
    const detailButton = screen.getByRole('button', { name: /查看详细分析/i });
    fireEvent.click(detailButton);
    
    expect(screen.getByText('AI写作分析详情')).toBeInTheDocument();
    expect(screen.getByText('检测到的AI特征')).toBeInTheDocument();
  });

  it('should show warning alert for high AI score', () => {
    const highScoreProps = {
      ...defaultProps,
      aiScore: 0.8
    };
    
    render(<HumanizedWritingControl {...highScoreProps} />);
    
    expect(screen.getByText('AI痕迹较明显')).toBeInTheDocument();
    expect(screen.getByText('建议点击"立即优化"来提升内容的人性化程度')).toBeInTheDocument();
  });
});