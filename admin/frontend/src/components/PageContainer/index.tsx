import { FC, ReactNode } from 'react'
import { Space, Typography, Breadcrumb } from 'antd'
import { HomeOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import styled from '@emotion/styled'

const { Title, Text } = Typography

interface BreadcrumbItem {
  title: string
  path?: string
}

interface PageContainerProps {
  title: string
  description?: string
  icon?: ReactNode
  extra?: ReactNode
  breadcrumb?: BreadcrumbItem[]
  children: ReactNode
}

const Header = styled.div`
  margin-bottom: 24px;
`

const TitleRow = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
`

const TitleSection = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
`

const IconWrapper = styled.div`
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: white;
  box-shadow: 0 4px 12px rgba(14, 165, 233, 0.3);
`

const PageContainer: FC<PageContainerProps> = ({
  title,
  description,
  icon,
  extra,
  breadcrumb,
  children,
}) => {
  const breadcrumbItems = breadcrumb?.map((item, index) => ({
    key: index,
    title: item.path ? (
      <Link to={item.path} style={{ color: 'rgba(250, 250, 250, 0.45)' }}>
        {item.title}
      </Link>
    ) : (
      <span style={{ color: 'rgba(250, 250, 250, 0.85)' }}>{item.title}</span>
    ),
  }))

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.3 }}
    >
      <Header>
        {breadcrumb && breadcrumb.length > 0 && (
          <Breadcrumb
            items={[
              {
                key: 'home',
                title: (
                  <Link to="/dashboard" style={{ color: 'rgba(250, 250, 250, 0.45)' }}>
                    <HomeOutlined />
                  </Link>
                ),
              },
              ...breadcrumbItems!,
            ]}
            style={{ marginBottom: 16 }}
          />
        )}
        
        <TitleRow>
          <TitleSection>
            {icon && <IconWrapper>{icon}</IconWrapper>}
            <div>
              <Title 
                level={4} 
                style={{ 
                  margin: 0, 
                  color: '#fafafa',
                  fontWeight: 700,
                  fontSize: 24,
                  letterSpacing: '-0.5px',
                }}
              >
                {title}
              </Title>
              {description && (
                <Text 
                  style={{ 
                    color: 'rgba(250, 250, 250, 0.45)',
                    fontSize: 14,
                    marginTop: 4,
                    display: 'block',
                  }}
                >
                  {description}
                </Text>
              )}
            </div>
          </TitleSection>
          
          {extra && (
            <Space size={12}>
              {extra}
            </Space>
          )}
        </TitleRow>
      </Header>
      
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.1 }}
      >
        {children}
      </motion.div>
    </motion.div>
  )
}

export default PageContainer
