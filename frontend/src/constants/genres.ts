/**
 * 小说分类常量
 * 确保前后端使用一致的分类定义
 */
export const NOVEL_GENRES = {
  XUANHUAN: 'xuanhuan',
  QIHUAN: 'qihuan',
  WUXIA: 'wuxia',
  XIANXIA: 'xianxia',
  KEHUAN: 'kehuan',
  YANQING: 'yanqing',
  XUANYI: 'xuanyi',
  JINGXUAN: 'jingxuan',
  LISHI: 'lishi',
  DUSHI: 'dushi',
  JUNSHI: 'junshi',
  YOUXI: 'youxi',
  TIYU: 'tiyu',
  QITA: 'qita'
} as const;

export const GENRE_LABELS: Record<string, string> = {
  [NOVEL_GENRES.XUANHUAN]: '玄幻',
  [NOVEL_GENRES.QIHUAN]: '奇幻',
  [NOVEL_GENRES.WUXIA]: '武侠',
  [NOVEL_GENRES.XIANXIA]: '仙侠',
  [NOVEL_GENRES.KEHUAN]: '科幻',
  [NOVEL_GENRES.YANQING]: '言情',
  [NOVEL_GENRES.XUANYI]: '悬疑',
  [NOVEL_GENRES.JINGXUAN]: '惊悚',
  [NOVEL_GENRES.LISHI]: '历史',
  [NOVEL_GENRES.DUSHI]: '都市',
  [NOVEL_GENRES.JUNSHI]: '军事',
  [NOVEL_GENRES.YOUXI]: '游戏',
  [NOVEL_GENRES.TIYU]: '体育',
  [NOVEL_GENRES.QITA]: '其他'
};

export const GENRE_OPTIONS = Object.entries(GENRE_LABELS).map(([key, label]) => ({
  value: label,  // 使用中文作为值保存到数据库
  label
}));

export type NovelGenre = typeof NOVEL_GENRES[keyof typeof NOVEL_GENRES]; 