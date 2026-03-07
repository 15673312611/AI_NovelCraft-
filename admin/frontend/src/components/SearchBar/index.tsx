import { Input, Button, Space } from 'antd'
import { SearchOutlined, FilterOutlined } from '@ant-design/icons'

interface SearchBarProps {
  placeholder?: string
  value?: string
  onChange?: (value: string) => void
  onSearch?: () => void
  showFilter?: boolean
  onFilterClick?: () => void
  loading?: boolean
}

const SearchBar = ({
  placeholder = '搜索...',
  value,
  onChange,
  onSearch,
  showFilter = false,
  onFilterClick,
  loading = false,
}: SearchBarProps) => {
  return (
    <Space size={12} style={{ width: '100%', maxWidth: 500 }}>
      <Input
        placeholder={placeholder}
        prefix={<SearchOutlined style={{ color: 'var(--text-tertiary)' }} />}
        value={value}
        onChange={(e) => onChange?.(e.target.value)}
        onPressEnter={onSearch}
        allowClear
        style={{ flex: 1 }}
      />
      <Button type="primary" icon={<SearchOutlined />} onClick={onSearch} loading={loading}>
        搜索
      </Button>
      {showFilter && (
        <Button icon={<FilterOutlined />} onClick={onFilterClick}>
          筛选
        </Button>
      )}
    </Space>
  )
}

export default SearchBar
