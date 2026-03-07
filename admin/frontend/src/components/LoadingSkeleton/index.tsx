import { Card, Skeleton } from 'antd'

interface LoadingSkeletonProps {
  type?: 'card' | 'table' | 'list'
  rows?: number
}

const LoadingSkeleton = ({ type = 'card', rows = 3 }: LoadingSkeletonProps) => {
  if (type === 'card') {
    return (
      <Card>
        <Skeleton active paragraph={{ rows: 4 }} />
      </Card>
    )
  }

  if (type === 'table') {
    return (
      <Card styles={{ body: { padding: 0 } }}>
        <div style={{ padding: '20px' }}>
          <Skeleton active paragraph={{ rows }} />
        </div>
      </Card>
    )
  }

  return <Skeleton active paragraph={{ rows }} />
}

export default LoadingSkeleton
