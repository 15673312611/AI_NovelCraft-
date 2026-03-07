import { ReactNode } from 'react'
import { Table, Card, Input, Button, Empty } from 'antd'
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons'
import type { TableProps, TablePaginationConfig } from 'antd'
import styled from '@emotion/styled'

interface DataTableProps<T> extends Omit<TableProps<T>, 'title'> {
  title?: string
  description?: string
  searchPlaceholder?: string
  searchValue?: string
  onSearchChange?: (value: string) => void
  onSearch?: () => void
  onRefresh?: () => void
  extra?: ReactNode
  toolbar?: ReactNode
  showSearch?: boolean
}

const TableCard = styled(Card)`
  .ant-card-head {
    padding: 16px 24px;
    min-height: auto;
  }
  
  .ant-card-body {
    padding: 0;
  }
  
  .ant-table {
    border-radius: 0;
  }
  
  .ant-table-wrapper {
    border: none;
    border-radius: 0;
  }
`

const HeaderWrapper = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
`

const TitleSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
`

const ToolbarSection = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
`

function DataTable<T extends object>({
  title,
  description,
  searchPlaceholder = '搜索...',
  searchValue,
  onSearchChange,
  onSearch,
  onRefresh,
  extra,
  toolbar,
  showSearch = true,
  loading,
  dataSource,
  pagination,
  ...tableProps
}: DataTableProps<T>) {
  const defaultPagination: TablePaginationConfig = {
    showSizeChanger: true,
    showQuickJumper: true,
    showTotal: (total) => (
      <span style={{ color: 'rgba(250, 250, 250, 0.45)' }}>
        共 <span style={{ color: '#0ea5e9', fontWeight: 600 }}>{total}</span> 条记录
      </span>
    ),
    pageSizeOptions: ['10', '20', '50', '100'],
    ...pagination,
  }

  const cardTitle = (
    <HeaderWrapper>
      {(title || description) && (
        <TitleSection>
          {title && (
            <span style={{ fontSize: 16, fontWeight: 600, color: '#fafafa' }}>
              {title}
            </span>
          )}
          {description && (
            <span style={{ fontSize: 13, color: 'rgba(250, 250, 250, 0.45)' }}>
              {description}
            </span>
          )}
        </TitleSection>
      )}
      
      <ToolbarSection>
        {showSearch && (
          <Input
            placeholder={searchPlaceholder}
            prefix={<SearchOutlined style={{ color: 'rgba(250, 250, 250, 0.25)' }} />}
            value={searchValue}
            onChange={(e) => onSearchChange?.(e.target.value)}
            onPressEnter={onSearch}
            style={{ width: 280 }}
            allowClear
          />
        )}
        
        {onRefresh && (
          <Button 
            icon={<ReloadOutlined />} 
            onClick={onRefresh}
          >
            刷新
          </Button>
        )}
        
        {toolbar}
        {extra}
      </ToolbarSection>
    </HeaderWrapper>
  )

  return (
    <TableCard title={cardTitle}>
      <Table<T>
        loading={loading}
        dataSource={dataSource}
        pagination={dataSource && dataSource.length > 0 ? defaultPagination : false}
        locale={{
          emptyText: (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <span style={{ color: 'rgba(250, 250, 250, 0.45)' }}>
                  暂无数据
                </span>
              }
              style={{ padding: '48px 0' }}
            />
          ),
        }}
        {...tableProps}
      />
    </TableCard>
  )
}

export default DataTable
