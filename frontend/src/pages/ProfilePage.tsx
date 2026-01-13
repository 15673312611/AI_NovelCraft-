import React, { useState, useEffect } from 'react'
import { Form, Input, Button, message, Spin, Avatar } from 'antd'
import { UserOutlined, MailOutlined, LockOutlined, SafetyOutlined, CheckCircleFilled } from '@ant-design/icons'
import { useSelector, useDispatch } from 'react-redux'
import { RootState, AppDispatch } from '@/store'
import { getProfile } from '@/store/slices/authSlice'
import { authService } from '@/services/authService'
import './ProfilePage.css'

const ProfilePage: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>()
  const { user, loading } = useSelector((state: RootState) => state.auth)
  const [passwordForm] = Form.useForm()
  const [changingPassword, setChangingPassword] = useState(false)

  useEffect(() => {
    dispatch(getProfile())
  }, [dispatch])

  const onChangePassword = async () => {
    try {
      const values = await passwordForm.validateFields()
      if (values.newPassword !== values.confirmPassword) {
        message.error('两次输入的密码不一致')
        return
      }
      setChangingPassword(true)
      await authService.changePassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      })
      message.success('密码修改成功')
      passwordForm.resetFields()
    } catch (error: any) {
      message.error(error?.message || '密码修改失败')
    } finally {
      setChangingPassword(false)
    }
  }

  if (loading && !user) {
    return (
      <div className="profile-page-v2">
        <div className="profile-loading-v2">
          <Spin size="large" />
        </div>
      </div>
    )
  }

  return (
    <div className="profile-page-v2">
      <div className="profile-container-v2">
        {/* 页面标题 */}
        <div className="profile-header-v2">
          <h1>个人资料</h1>
          <p>管理您的账户信息和安全设置</p>
        </div>

        {/* 主内容区 - 双栏布局 */}
        <div className="profile-grid-v2">
          {/* 左侧 - 用户信息卡片 */}
          <div className="profile-card-v2 profile-user-card">
            <div className="profile-user-header">
              <div className="profile-avatar-wrapper-v2">
                <Avatar 
                  size={72} 
                  className="profile-avatar-v2"
                  style={{ 
                    background: 'linear-gradient(145deg, #3b82f6 0%, #1d4ed8 100%)',
                    fontSize: '28px',
                    fontWeight: 600
                  }}
                >
                  {user?.username?.[0]?.toUpperCase() || 'U'}
                </Avatar>
                <div className="profile-avatar-badge-v2">
                  <CheckCircleFilled />
                </div>
              </div>
              <div className="profile-user-info">
                <h2>{user?.username || '用户'}</h2>
                <span className="profile-status-tag">
                  <span className="status-dot"></span>
                  账户正常
                </span>
              </div>
            </div>
            <div className="profile-info-list">
              <div className="profile-info-row">
                <div className="profile-info-icon-v2">
                  <UserOutlined />
                </div>
                <div className="profile-info-text">
                  <span className="label">用户名</span>
                  <span className="value">{user?.username || '-'}</span>
                </div>
                <span className="profile-readonly-tag">不可修改</span>
              </div>
              <div className="profile-info-row">
                <div className="profile-info-icon-v2">
                  <MailOutlined />
                </div>
                <div className="profile-info-text">
                  <span className="label">邮箱地址</span>
                  <span className="value">{user?.email || '-'}</span>
                </div>
                <span className="profile-readonly-tag">不可修改</span>
              </div>
            </div>
          </div>

          {/* 右侧 - 安全设置卡片 */}
          <div className="profile-card-v2 profile-security-card">
            <div className="profile-card-title-v2">
              <div className="profile-card-icon-v2">
                <SafetyOutlined />
              </div>
              <div>
                <h3>安全设置</h3>
                <p>修改您的登录密码</p>
              </div>
            </div>
            <Form 
              form={passwordForm} 
              layout="vertical" 
              className="profile-form-v2"
            >
              <Form.Item
                label="当前密码"
                name="oldPassword"
                rules={[{ required: true, message: '请输入当前密码' }]}
              >
                <Input.Password 
                  prefix={<LockOutlined className="input-icon-v2" />}
                  placeholder="输入当前密码" 
                  className="profile-input-v2"
                />
              </Form.Item>
              <div className="profile-form-row-v2">
                <Form.Item
                  label="新密码"
                  name="newPassword"
                  rules={[
                    { required: true, message: '请输入新密码' },
                    { min: 6, message: '密码至少6个字符' }
                  ]}
                >
                  <Input.Password 
                    prefix={<LockOutlined className="input-icon-v2" />}
                    placeholder="输入新密码" 
                    className="profile-input-v2"
                  />
                </Form.Item>
                <Form.Item
                  label="确认密码"
                  name="confirmPassword"
                  rules={[
                    { required: true, message: '请确认新密码' },
                    ({ getFieldValue }) => ({
                      validator(_, value) {
                        if (!value || getFieldValue('newPassword') === value) {
                          return Promise.resolve()
                        }
                        return Promise.reject(new Error('两次输入的密码不一致'))
                      },
                    }),
                  ]}
                >
                  <Input.Password 
                    prefix={<LockOutlined className="input-icon-v2" />}
                    placeholder="确认新密码" 
                    className="profile-input-v2"
                  />
                </Form.Item>
              </div>
              <Button 
                type="primary" 
                onClick={onChangePassword} 
                loading={changingPassword}
                className="profile-submit-btn-v2"
              >
                修改密码
              </Button>
            </Form>
          </div>
        </div>

        {/* 底部提示 */}
        <div className="profile-tips-v2">
          <SafetyOutlined />
          <span>为保护账户安全，用户名和邮箱一经注册不可修改，建议定期更换密码</span>
        </div>
      </div>
    </div>
  )
}

export default ProfilePage
