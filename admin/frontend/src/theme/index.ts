import type { ThemeConfig } from 'antd'

// 企业级深色主题配置
export const darkTheme: ThemeConfig = {
  token: {
    // 品牌色 - 现代蓝绿色系
    colorPrimary: '#0ea5e9',
    colorInfo: '#3b82f6',
    colorSuccess: '#22c55e',
    colorWarning: '#f97316',
    colorError: '#ef4444',
    
    // 背景色
    colorBgContainer: '#171717',
    colorBgElevated: '#1c1c1c',
    colorBgLayout: '#0a0a0a',
    colorBgSpotlight: '#262626',
    colorBgMask: 'rgba(0, 0, 0, 0.6)',
    
    // 文字色
    colorText: '#fafafa',
    colorTextSecondary: 'rgba(250, 250, 250, 0.65)',
    colorTextTertiary: 'rgba(250, 250, 250, 0.45)',
    colorTextQuaternary: 'rgba(250, 250, 250, 0.25)',
    
    // 边框色
    colorBorder: 'rgba(255, 255, 255, 0.08)',
    colorBorderSecondary: 'rgba(255, 255, 255, 0.06)',
    
    // 填充色
    colorFill: 'rgba(255, 255, 255, 0.08)',
    colorFillSecondary: 'rgba(255, 255, 255, 0.06)',
    colorFillTertiary: 'rgba(255, 255, 255, 0.04)',
    colorFillQuaternary: 'rgba(255, 255, 255, 0.02)',
    
    // 圆角
    borderRadius: 8,
    borderRadiusLG: 12,
    borderRadiusSM: 6,
    borderRadiusXS: 4,
    
    // 字体
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "SF Pro Display", Roboto, "Helvetica Neue", Arial, sans-serif',
    fontSize: 14,
    fontSizeLG: 16,
    fontSizeSM: 12,
    fontSizeXL: 20,
    
    // 行高
    lineHeight: 1.5714285714285714,
    lineHeightLG: 1.5,
    lineHeightSM: 1.6666666666666667,
    
    // 控件高度
    controlHeight: 38,
    controlHeightLG: 44,
    controlHeightSM: 32,
    
    // 间距
    padding: 16,
    paddingLG: 24,
    paddingSM: 12,
    paddingXS: 8,
    paddingXXS: 4,
    
    margin: 16,
    marginLG: 24,
    marginSM: 12,
    marginXS: 8,
    marginXXS: 4,
    
    // 阴影
    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.4)',
    boxShadowSecondary: '0 8px 24px rgba(0, 0, 0, 0.5)',
    
    // 动画
    motionDurationFast: '0.1s',
    motionDurationMid: '0.2s',
    motionDurationSlow: '0.3s',
    motionEaseInOut: 'cubic-bezier(0.4, 0, 0.2, 1)',
    motionEaseOut: 'cubic-bezier(0, 0, 0.2, 1)',
  },
  components: {
    Layout: {
      headerBg: 'rgba(23, 23, 23, 0.85)',
      siderBg: '#171717',
      bodyBg: '#0a0a0a',
      headerHeight: 64,
      headerPadding: '0 24px',
    },
    Menu: {
      darkItemBg: 'transparent',
      darkSubMenuItemBg: 'transparent',
      darkItemSelectedBg: 'rgba(14, 165, 233, 0.15)',
      darkItemSelectedColor: '#0ea5e9',
      darkItemHoverBg: 'rgba(255, 255, 255, 0.06)',
      darkItemHoverColor: '#fafafa',
      itemBorderRadius: 8,
      itemMarginInline: 8,
      itemHeight: 44,
      iconSize: 18,
      collapsedIconSize: 18,
    },
    Card: {
      headerBg: 'transparent',
      colorBgContainer: '#171717',
      borderRadiusLG: 16,
      paddingLG: 24,
    },
    Table: {
      headerBg: 'rgba(255, 255, 255, 0.02)',
      headerColor: 'rgba(250, 250, 250, 0.65)',
      rowHoverBg: 'rgba(14, 165, 233, 0.06)',
      borderColor: 'rgba(255, 255, 255, 0.06)',
      cellPaddingBlock: 16,
      cellPaddingInline: 16,
      headerBorderRadius: 0,
    },
    Button: {
      borderRadius: 8,
      borderRadiusLG: 10,
      borderRadiusSM: 6,
      controlHeight: 40,
      controlHeightLG: 48,
      controlHeightSM: 32,
      paddingInline: 20,
      paddingInlineLG: 24,
      paddingInlineSM: 12,
      fontWeight: 500,
      primaryShadow: '0 2px 8px rgba(14, 165, 233, 0.35)',
    },
    Input: {
      borderRadius: 8,
      borderRadiusLG: 10,
      borderRadiusSM: 6,
      controlHeight: 40,
      controlHeightLG: 48,
      controlHeightSM: 32,
      paddingInline: 14,
      colorBgContainer: 'rgba(255, 255, 255, 0.04)',
      hoverBorderColor: 'rgba(14, 165, 233, 0.5)',
      activeBorderColor: '#0ea5e9',
      activeShadow: '0 0 0 3px rgba(14, 165, 233, 0.12)',
    },
    Select: {
      borderRadius: 8,
      controlHeight: 40,
      controlHeightLG: 48,
      controlHeightSM: 32,
      optionSelectedBg: 'rgba(14, 165, 233, 0.15)',
      optionActiveBg: 'rgba(255, 255, 255, 0.06)',
      colorBgContainer: 'rgba(255, 255, 255, 0.04)',
    },
    Modal: {
      borderRadiusLG: 16,
      paddingContentHorizontalLG: 28,
      paddingMD: 24,
      headerBg: 'transparent',
      contentBg: '#1c1c1c',
      footerBg: 'transparent',
    },
    Dropdown: {
      borderRadiusLG: 12,
      paddingBlock: 6,
      controlItemBgHover: 'rgba(255, 255, 255, 0.06)',
      controlItemBgActive: 'rgba(14, 165, 233, 0.15)',
    },
    Tag: {
      borderRadiusSM: 6,
      defaultBg: 'rgba(255, 255, 255, 0.06)',
    },
    Badge: {
      dotSize: 8,
      statusSize: 8,
    },
    Tabs: {
      itemSelectedColor: '#0ea5e9',
      itemHoverColor: '#fafafa',
      inkBarColor: '#0ea5e9',
      cardBg: 'rgba(255, 255, 255, 0.04)',
    },
    Form: {
      labelColor: 'rgba(250, 250, 250, 0.65)',
      labelFontSize: 14,
      verticalLabelPadding: '0 0 8px',
    },
    Statistic: {
      titleFontSize: 13,
      contentFontSize: 28,
    },
    Progress: {
      defaultColor: '#0ea5e9',
      remainingColor: 'rgba(255, 255, 255, 0.08)',
    },
    Tooltip: {
      colorBgSpotlight: '#262626',
      borderRadius: 8,
    },
    Message: {
      contentBg: '#262626',
      borderRadiusLG: 12,
    },
    Notification: {
      colorBgElevated: '#262626',
      borderRadiusLG: 16,
    },
    Pagination: {
      itemBg: 'rgba(255, 255, 255, 0.04)',
      itemActiveBg: '#0ea5e9',
      borderRadius: 8,
    },
    Switch: {
      colorPrimary: '#0ea5e9',
      colorPrimaryHover: '#0284c7',
    },
    Slider: {
      trackBg: 'rgba(255, 255, 255, 0.15)',
      trackHoverBg: 'rgba(255, 255, 255, 0.2)',
      railBg: 'rgba(255, 255, 255, 0.08)',
      railHoverBg: 'rgba(255, 255, 255, 0.12)',
      handleColor: '#0ea5e9',
      handleActiveColor: '#0284c7',
    },
  },
}

// 设计令牌 - 用于自定义组件
export const designTokens = {
  colors: {
    primary: '#0ea5e9',
    primaryHover: '#0284c7',
    primaryLight: 'rgba(14, 165, 233, 0.15)',
    secondary: '#06b6d4',
    success: '#22c55e',
    successLight: 'rgba(34, 197, 94, 0.15)',
    warning: '#f97316',
    warningLight: 'rgba(249, 115, 22, 0.15)',
    error: '#ef4444',
    errorLight: 'rgba(239, 68, 68, 0.15)',
    info: '#3b82f6',
    infoLight: 'rgba(59, 130, 246, 0.15)',
    purple: '#a855f7',
    purpleLight: 'rgba(168, 85, 247, 0.15)',
  },
  gradients: {
    primary: 'linear-gradient(135deg, #0ea5e9, #06b6d4)',
    success: 'linear-gradient(135deg, #22c55e, #16a34a)',
    warning: 'linear-gradient(135deg, #f97316, #ea580c)',
    error: 'linear-gradient(135deg, #ef4444, #dc2626)',
    purple: 'linear-gradient(135deg, #a855f7, #9333ea)',
    blue: 'linear-gradient(135deg, #3b82f6, #2563eb)',
  },
  shadows: {
    sm: '0 2px 4px rgba(0, 0, 0, 0.3)',
    md: '0 4px 12px rgba(0, 0, 0, 0.4)',
    lg: '0 8px 24px rgba(0, 0, 0, 0.5)',
    xl: '0 16px 48px rgba(0, 0, 0, 0.6)',
    glow: {
      primary: '0 0 20px rgba(14, 165, 233, 0.4)',
      success: '0 0 20px rgba(34, 197, 94, 0.4)',
      warning: '0 0 20px rgba(249, 115, 22, 0.4)',
      error: '0 0 20px rgba(239, 68, 68, 0.4)',
    },
  },
  transitions: {
    fast: '0.15s cubic-bezier(0.4, 0, 0.2, 1)',
    base: '0.2s cubic-bezier(0.4, 0, 0.2, 1)',
    slow: '0.3s cubic-bezier(0.4, 0, 0.2, 1)',
  },
}

export default darkTheme
