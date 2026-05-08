import { loginByPassword, loginBySms, register } from '@/utils/api';
import { saveLoginInfo } from '@/utils/useUser';
import {
  LockOutlined,
  MailOutlined,
  PhoneOutlined,
  SafetyOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { Button, Card, Form, Input, message } from 'antd';
import { useState } from 'react';
import styles from './index.less';

type Mode = 'login' | 'register';

interface LoginForm {
  username: string;
  password: string;
}

interface SmsForm {
  phone: string;
  code: string;
}

interface RegisterForm {
  username: string;
  password: string;
  confirmPassword: string;
  nickname?: string;
  phone?: string;
  email?: string;
}

const LoginPage: React.FC = () => {
  const [mode, setMode] = useState<Mode>('login');
  const [loginType, setLoginType] = useState<'account' | 'sms'>('account');
  const [loading, setLoading] = useState(false);
  const [smsCountdown, setSmsCountdown] = useState(0);
  const [accountForm] = Form.useForm<LoginForm>();
  const [smsForm] = Form.useForm<SmsForm>();
  const [registerForm] = Form.useForm<RegisterForm>();

  // 账号密码登录
  const handleAccountLogin = async (values: LoginForm) => {
    setLoading(true);
    try {
      const response = await loginByPassword(values);
      if (response.code === 200) {
        saveLoginInfo(response.data.token, response.data.user);
        message.success('登录成功！');
        window.location.href = '/home';
      } else {
        message.error(response.message || '登录失败');
        setLoading(false);
      }
    } catch (error: any) {
      message.error(error?.message || '登录失败，请检查用户名和密码');
      setLoading(false);
    }
  };

  // 发送验证码
  const handleSendSms = async () => {
    const phone = smsForm.getFieldValue('phone');
    if (!phone) {
      message.warning('请先输入手机号');
      return;
    }
    message.success('验证码已发送');
    setSmsCountdown(60);
    const timer = setInterval(() => {
      setSmsCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
  };

  // 验证码登录
  const handleSmsLogin = async (values: SmsForm) => {
    setLoading(true);
    try {
      const response = await loginBySms(values);
      if (response.code === 200) {
        saveLoginInfo(response.data.token, response.data.user);
        message.success('登录成功！');
        window.location.href = '/home';
      } else {
        message.error(response.message || '登录失败');
        setLoading(false);
      }
    } catch (error: any) {
      message.error(error?.message || '登录失败，请检查手机号和验证码');
      setLoading(false);
    }
  };

  // 注册
  const handleRegister = async (values: RegisterForm) => {
    if (values.password !== values.confirmPassword) {
      message.error('两次输入的密码不一致');
      return;
    }
    setLoading(true);
    try {
      const response = await register({
        username: values.username,
        password: values.password,
        nickname: values.nickname,
        phone: values.phone,
        email: values.email,
      });
      if (response.code === 200) {
        saveLoginInfo(response.data.token, response.data.user);
        message.success('注册成功！');
        window.location.href = '/home';
      } else {
        message.error(response.message || '注册失败');
        setLoading(false);
      }
    } catch (error: any) {
      message.error(error?.message || '注册失败，请稍后重试');
      setLoading(false);
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.loginBox}>
        <div className={styles.titleSection}>
          <h1 className={styles.title}>
            {mode === 'login' ? '天天市场' : '欢迎注册'}
          </h1>
          <p className={styles.subtitle}>
            {mode === 'login'
              ? '这里有你想淘到的宝贝'
              : ''}
          </p>
        </div>

        <Card className={styles.card}>
          {/* 登录表单 */}
          {mode === 'login' && (
            <>
              <div className={styles.switchTabs}>
                <span
                  className={loginType === 'account' ? styles.active : ''}
                  onClick={() => {
                    setLoginType('account');
                    setLoading(false);
                  }}
                >
                  账号密码登录
                </span>
                <span
                  className={loginType === 'sms' ? styles.active : ''}
                  onClick={() => {
                    setLoginType('sms');
                    setLoading(false);
                  }}
                >
                  验证码登录
                </span>
              </div>

              {loginType === 'account' && (
                <Form
                  form={accountForm}
                  name="accountLogin"
                  onFinish={handleAccountLogin}
                  autoComplete="off"
                  size="large"
                >
                  <Form.Item
                    name="username"
                    rules={[
                      { required: true, message: '请输入用户名' },
                      { min: 3, message: '用户名至少3个字符' },
                    ]}
                  >
                    <Input
                      prefix={<UserOutlined />}
                      placeholder="请输入用户名"
                      className={styles.glassInput}
                    />
                  </Form.Item>
                  <Form.Item
                    name="password"
                    rules={[
                      { required: true, message: '请输入密码' },
                      { min: 6, message: '密码至少6个字符' },
                    ]}
                  >
                    <Input.Password
                      prefix={<LockOutlined />}
                      placeholder="请输入密码"
                      className={styles.glassInput}
                    />
                  </Form.Item>
                  <Form.Item>
                    <Button
                      type="primary"
                      htmlType="submit"
                      loading={loading}
                      block
                      className={styles.submitBtn}
                    >
                      登 录
                    </Button>
                  </Form.Item>
                </Form>
              )}

              {loginType === 'sms' && (
                <Form
                  form={smsForm}
                  name="smsLogin"
                  onFinish={handleSmsLogin}
                  autoComplete="off"
                  size="large"
                >
                  <Form.Item
                    name="phone"
                    rules={[
                      { required: true, message: '请输入手机号' },
                      {
                        pattern: /^1[3-9]\d{9}$/,
                        message: '请输入正确的手机号',
                      },
                    ]}
                  >
                    <Input
                      prefix={<PhoneOutlined />}
                      placeholder="请输入手机号"
                      className={styles.glassInput}
                    />
                  </Form.Item>
                  <Form.Item
                    name="code"
                    rules={[{ required: true, message: '请输入验证码' }]}
                  >
                    <div className={styles.codeInput}>
                      <Input
                        prefix={<SafetyOutlined />}
                        placeholder="请输入验证码"
                        className={styles.glassInput}
                      />
                      <Button
                        className={styles.codeBtn}
                        onClick={handleSendSms}
                        disabled={smsCountdown > 0}
                      >
                        {smsCountdown > 0 ? `${smsCountdown}s` : '获取验证码'}
                      </Button>
                    </div>
                  </Form.Item>
                  <Form.Item>
                    <Button
                      type="primary"
                      htmlType="submit"
                      loading={loading}
                      block
                      className={styles.submitBtn}
                    >
                      登 录
                    </Button>
                  </Form.Item>
                </Form>
              )}
            </>
          )}

          {/* 注册表单 */}
          {mode === 'register' && (
            <Form
              form={registerForm}
              name="register"
              onFinish={handleRegister}
              autoComplete="off"
              size="large"
              layout="vertical"
            >
              <Form.Item
                label="用户名"
                name="username"
                rules={[
                  { required: true, message: '请输入用户名' },
                  { min: 3, max: 20, message: '用户名长度在3-20个字符之间' },
                ]}
              >
                <Input
                  prefix={<UserOutlined />}
                  placeholder="请输入用户名（3-20个字符）"
                  className={styles.glassInput}
                />
              </Form.Item>
              <Form.Item
                label="密码"
                name="password"
                rules={[
                  { required: true, message: '请输入密码' },
                  { min: 6, max: 20, message: '密码长度在6-20个字符之间' },
                ]}
              >
                <Input.Password
                  prefix={<LockOutlined />}
                  placeholder="请输入密码（6-20个字符）"
                  className={styles.glassInput}
                />
              </Form.Item>
              <Form.Item
                label="确认密码"
                name="confirmPassword"
                rules={[
                  { required: true, message: '请再次输入密码' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue('password') === value) {
                        return Promise.resolve();
                      }
                      return Promise.reject(new Error('两次输入的密码不一致'));
                    },
                  }),
                ]}
              >
                <Input.Password
                  prefix={<LockOutlined />}
                  placeholder="请再次输入密码"
                  className={styles.glassInput}
                />
              </Form.Item>
              <Form.Item label="昵称（选填）" name="nickname">
                <Input
                  prefix={<UserOutlined />}
                  placeholder="不填则默认使用用户名"
                  className={styles.glassInput}
                />
              </Form.Item>
              <Form.Item
                label="手机号（选填）"
                name="phone"
                rules={[
                  { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号' },
                ]}
              >
                <Input prefix={<PhoneOutlined />} placeholder="请输入手机号" className={styles.glassInput} />
              </Form.Item>
              <Form.Item
                label="邮箱（选填）"
                name="email"
                rules={[{ type: 'email', message: '请输入正确的邮箱地址' }]}
              >
                <Input prefix={<MailOutlined />} placeholder="请输入邮箱" className={styles.glassInput} />
              </Form.Item>
              <Form.Item style={{ marginBottom: 0 }}>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={loading}
                  block
                  className={styles.submitBtn}
                >
                  注 册
                </Button>
              </Form.Item>
            </Form>
          )}

          <div className={styles.footer}>
            {mode === 'login' ? (
              <>
                <a>忘记密码</a>
                <span className={styles.divider}>|</span>
                <a onClick={() => setMode('register')}>注册</a>
              </>
            ) : (
              <a onClick={() => setMode('login')}>已有账号？立即登录</a>
            )}
          </div>
        </Card>
      </div>
    </div>
  );
};

export default LoginPage;
