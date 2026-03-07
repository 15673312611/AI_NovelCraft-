import React from 'react';

interface NovelCardIconProps {
  className?: string;
  style?: React.CSSProperties;
}

/**
 * 现代风格的书籍图标 SVG
 * 用于小说列表页的封面占位
 */
const NovelCardIcon: React.FC<NovelCardIconProps> = ({ className, style }) => {
  const uniqueId = React.useId().replace(/:/g, '');
  const coverGradientId = `coverGradient-${uniqueId}`;
  const pageGradientId = `pageGradient-${uniqueId}`;
  const shadowId = `softShadow-${uniqueId}`;

  return (
    <svg
      viewBox="0 0 200 200"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      style={style}
    >
      <defs>
        <linearGradient id={coverGradientId} x1="20" y1="20" x2="160" y2="180" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.9" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.7" />
        </linearGradient>
        
        <linearGradient id={pageGradientId} x1="0" y1="0" x2="0" y2="1" gradientUnits="objectBoundingBox">
          <stop offset="0" stopColor="#ffffff" />
          <stop offset="1" stopColor="#f1f5f9" />
        </linearGradient>
        
        <filter id={shadowId} x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur in="SourceAlpha" stdDeviation="4" />
          <feOffset dx="2" dy="4" result="offsetblur" />
          <feComponentTransfer>
            <feFuncA type="linear" slope="0.2" />
          </feComponentTransfer>
          <feMerge>
            <feMergeNode />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      {/* 书本主体组，稍微旋转或倾斜 */}
      <g transform="translate(40, 30) rotate(-5 100 100)">
        
        {/* 后封面 (底部) */}
        <rect x="10" y="10" width="120" height="150" rx="4" fill="currentColor" fillOpacity="0.3" />
        
        {/* 书页层 (中间的白色部分) */}
        <path 
          d="M15 15 H130 V160 H15 V15 Z" 
          fill={`url(#${pageGradientId})`}
          stroke="#e2e8f0" 
          strokeWidth="1"
        />
        
        {/* 侧边页码纹理 */}
        <path d="M130 20 H138 V155 H130 V20 Z" fill="#f8fafc" stroke="#cbd5e1" strokeWidth="0.5" />
        <line x1="130" y1="30" x2="138" y2="30" stroke="#cbd5e1" strokeWidth="0.5" />
        <line x1="130" y1="45" x2="138" y2="45" stroke="#cbd5e1" strokeWidth="0.5" />
        <line x1="130" y1="60" x2="138" y2="60" stroke="#cbd5e1" strokeWidth="0.5" />
        <line x1="130" y1="75" x2="138" y2="75" stroke="#cbd5e1" strokeWidth="0.5" />
        <line x1="130" y1="90" x2="138" y2="90" stroke="#cbd5e1" strokeWidth="0.5" />
        <line x1="130" y1="105" x2="138" y2="105" stroke="#cbd5e1" strokeWidth="0.5" />
        <line x1="130" y1="120" x2="138" y2="120" stroke="#cbd5e1" strokeWidth="0.5" />
        <line x1="130" y1="135" x2="138" y2="135" stroke="#cbd5e1" strokeWidth="0.5" />

        {/* 前封面 */}
        <path 
          d="M5 5 H125 C127.209 5 129 6.79086 129 9 V151 C129 153.209 127.209 155 125 155 H5 C2.79086 155 1 153.209 1 151 V9 C1 6.79086 2.79086 5 5 5 Z" 
          fill={`url(#${coverGradientId})`}
          filter={`url(#${shadowId})`}
        />

        {/* 书脊亮部高光 */}
        <path d="M5 5 H15 V155 H5 V5 Z" fill="currentColor" fillOpacity="0.2" />
        
        {/* 封面装饰：极简线条 */}
        <rect x="30" y="40" width="60" height="6" rx="2" fill="white" fillOpacity="0.4" />
        <rect x="30" y="52" width="40" height="6" rx="2" fill="white" fillOpacity="0.4" />
        
      </g>
      
      {/* 额外的装饰元素：漂浮的粒子/光点 */}
      <circle cx="160" cy="40" r="4" fill="currentColor" fillOpacity="0.2" />
      <circle cx="170" cy="55" r="2" fill="currentColor" fillOpacity="0.3" />
      <circle cx="30" cy="160" r="3" fill="currentColor" fillOpacity="0.2" />
      
    </svg>
  );
};

export default NovelCardIcon;
