import {
  CAMPUS_LOCATIONS,
  CONDITION_OPTIONS,
  PRODUCT_CATEGORIES,
  SERVICE_TYPES,
} from '@/constants/campus';
import { createProduct, createService } from '@/utils/api';
import {
  BookOutlined,
  CameraOutlined,
  LaptopOutlined,
  PlusOutlined,
  RobotOutlined,
  ShoppingCartOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  message,
  notification,
  Radio,
  Select,
  Tag,
  Upload,
} from 'antd';
import { useState } from 'react';
import styles from './index.less';
const { TextArea } = Input;
const { Option } = Select;

type PublishKind = 'select' | 'product' | 'service';

/**
 * 发布页：固定大小三段式
 *  1. select  : 选择发布商品 / 发布服务
 *  2. product : 发布商品表单（固定 960px、字段高度统一）
 *  3. service : 发布服务表单（同上）
 */
const PublishPage: React.FC = () => {
  const [kind, setKind] = useState<PublishKind>('select');
  const [form] = Form.useForm();
  const [serviceForm] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [images, setImages] = useState<string[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [serviceTags, setServiceTags] = useState<string[]>([]);
  const [tagInput, setTagInput] = useState('');
  const [aiGenerating, setAiGenerating] = useState(false);

  // 切换类型时清理状态
  const switchKind = (next: PublishKind) => {
    setKind(next);
    if (next === 'select') {
      form.resetFields();
      serviceForm.resetFields();
      setImages([]);
      setServiceTags([]);
      setSelectedCategory('');
    }
  };

  // ============== 图片上传 ==============
  const uploadRequest = async (options: any) => {
    const { file, onSuccess, onError } = options;
    const formData = new FormData();
    formData.append('file', file);
    try {
      const res = await fetch('/api/upload', {
        method: 'POST',
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
        body: formData,
      });
      const data = await res.json();
      if (data.code === 200) {
        const url = data.data;
        setImages((prev) => [...prev, url]);
        onSuccess({ url }, file);
      } else {
        onError(new Error(data.message));
      }
    } catch (e) {
      onError(e);
    }
  };

  // ============== 标签 ==============
  const handleAddTag = () => {
    if (tagInput && tagInput.trim() && serviceTags.length < 5) {
      setServiceTags([...serviceTags, tagInput.trim()]);
      setTagInput('');
    }
  };
  const handleRemoveTag = (tag: string) => {
    setServiceTags(serviceTags.filter((t) => t !== tag));
  };

  // ============== AI 生成 ==============
  const handleAIGenerate = async () => {
    const title = form.getFieldValue('title');
    const category = selectedCategory;
    if (!title || !title.trim()) {
      message.warning('请先填写商品名称');
      return;
    }
    if (aiGenerating) return;
    setAiGenerating(true);
    const timeoutId = window.setTimeout(() => {
      message.error('AI 响应超时，请稍后重试');
      setAiGenerating(false);
    }, 60000);
    try {
      const res = await fetch('/api/ai/generate-description', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('token')}`,
        },
        body: JSON.stringify({ title, category }),
      });
      const data = await res.json().catch(() => null);
      if (data && data.code === 200) {
        form.setFieldsValue({ description: data.data.description });
        if (data.data.priceSuggestion) {
          notification.success({
            message: 'AI 文案已生成',
            description: `价格建议：${data.data.priceSuggestion}（可作为参考）`,
            duration: 0,
            placement: 'top',
            btn: (
              <Button
                type="link"
                size="small"
                onClick={() => notification.destroy('ai-price-tip')}
              >
                我知道了
              </Button>
            ),
            key: 'ai-price-tip',
          });
        } else {
          message.success('AI 文案已生成，可继续编辑～');
        }
      } else {
        const code = data?.code;
        const msg = data?.message || '生成失败，请重试';
        switch (code) {
          case 429:
            message.warning(msg);
            break;
          case 401:
          case 402:
            message.error(msg);
            break;
          case 504:
            message.warning(msg || '响应超时，请稍后重试');
            break;
          case 502:
            message.error(msg || 'AI 服务暂时不可用，请稍后重试');
            break;
          default:
            message.error(msg);
        }
      }
    } catch {
      message.error('生成失败，请检查网络');
    } finally {
      window.clearTimeout(timeoutId);
      setAiGenerating(false);
    }
  };

  // ============== 提交 ==============
  const handleSubmitProduct = async () => {
    try {
      setLoading(true);
      const values = await form.validateFields().catch(({ errorFields }) => {
        const firstError = errorFields?.[0];
        if (firstError) {
          message.error(firstError.errors?.[0] || '请完善表单信息');
        }
        return null;
      });
      if (!values) return;

      const payload = {
        title: (values.title || '').trim(),
        description: (values.description || '').trim(),
        price: values.price,
        originalPrice: values.originalPrice,
        images,
        categoryId: values.categoryId,
        subCategory: values.subCategory,
        condition: values.condition,
        location: values.location,
        isNegotiable: values.isNegotiable ? 1 : 0,
        tags: serviceTags,
      };
      await createProduct(payload);
      message.success('商品发布成功！');
      history.push('/my-publish');
    } catch (error: any) {
      if (error?.response?.data?.message) {
        message.error(error.response.data.message);
      } else {
        message.error(error?.message || '发布失败，请稍后重试');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleSubmitService = async () => {
    try {
      setLoading(true);
      const values = await serviceForm.validateFields().catch((err: any) => {
        const fields = err?.errorFields;
        if (fields && fields.length > 0) {
          message.error(fields[0]?.errors?.[0] || '请完善表单信息');
        }
        return null;
      });
      if (!values) return;

      const payload = {
        title: (values.title || '').trim(),
        description: (values.description || '').trim(),
        price: values.price,
        priceUnit: values.priceUnit || '/次',
        serviceType: values.serviceType,
        location: values.location,
        isNegotiable: values.isNegotiable ? 1 : 0,
        tags: serviceTags,
      };
      await createService(payload);
      message.success('服务发布成功！');
      history.push('/my-publish');
    } catch (error: any) {
      if (error?.response?.data?.message) {
        message.error(error.response.data.message);
      } else {
        message.error(error?.message || '发布失败，请稍后重试');
      }
    } finally {
      setLoading(false);
    }
  };

  const getCategoryIcon = (iconType: string) => {
    const iconMap: Record<string, React.ReactNode> = {
      book: <BookOutlined />,
      laptop: <LaptopOutlined />,
      'shopping-cart': <ShoppingCartOutlined />,
      team: <TeamOutlined />,
    };
    return iconMap[iconType] || <ShoppingCartOutlined />;
  };

  const getSubCategories = () => {
    const category = PRODUCT_CATEGORIES.find((c) => c.id === selectedCategory);
    return category?.subCategories || [];
  };

  // ============== 渲染：选择类型 ==============
  if (kind === 'select') {
    return (
      <div className={styles.selectPage}>
        <div className={styles.selectHeader}>
          <h1 className={styles.selectTitle}>发布新宝贝</h1>
          <p className={styles.selectSubtitle}>选择发布类型，开始你的校园交易</p>
        </div>
        <div className={styles.typeGrid}>
          <div className={styles.typeCard} onClick={() => switchKind('product')}>
            <div className={`${styles.typeIcon} ${styles.typeIconProduct}`}>
              <ShoppingCartOutlined />
            </div>
            <div className={styles.typeName}>发布商品</div>
            <div className={styles.typeDesc}>
              二手书 / 数码 / 服饰 / 闲置物品
            </div>
          </div>
          <div className={styles.typeCard} onClick={() => switchKind('service')}>
            <div className={`${styles.typeIcon} ${styles.typeIconService}`}>
              <TeamOutlined />
            </div>
            <div className={styles.typeName}>发布服务</div>
            <div className={styles.typeDesc}>
              取快递 / 带外卖 / 打印 / 跑腿代买
            </div>
          </div>
        </div>
      </div>
    );
  }

  // ============== 渲染：商品表单 ==============
  if (kind === 'product') {
    return (
      <div className={styles.formPage}>
        <div className={styles.formHeader}>
          <Button
            type="link"
            className={styles.backBtn}
            onClick={() => switchKind('select')}
          >
            ← 返回选择
          </Button>
          <h2 className={styles.formTitle}>发布商品</h2>
        </div>
        <div className={styles.formCard}>
          <Form
            form={form}
            layout="vertical"
            className={styles.form}
            initialValues={{ isNegotiable: true, condition: '轻微使用' }}
          >
            {/* 左列：基础信息 */}
            <div className={styles.formLeft}>
              <Form.Item
                name="title"
                label="商品名称"
                rules={[{ required: true, message: '请输入商品名称' }]}
              >
                <Input
                  placeholder="尽量输入详细名称、数量等"
                  maxLength={50}
                  showCount
                  className={styles.fixedInput}
                />
              </Form.Item>

              <Form.Item
                name="description"
                label="商品描述"
                rules={[{ required: true, message: '请输入商品描述' }]}
              >
                <TextArea
                  placeholder="详细描述成色、使用情况、转手原因等"
                  maxLength={500}
                  showCount
                  className={styles.fixedTextarea}
                />
              </Form.Item>

              <div className={styles.aiBar}>
                <Button
                  icon={<RobotOutlined />}
                  loading={aiGenerating}
                  onClick={handleAIGenerate}
                  className={styles.aiBtn}
                >
                  {aiGenerating ? 'AI 生成中...' : 'AI 一键生成商品描述'}
                </Button>
              </div>

              {/* 价格两栏 */}
              <div className={styles.row2}>
                <Form.Item
                  name="price"
                  label="出售价格"
                  rules={[{ required: true, message: '请输入价格' }]}
                >
                  <InputNumber
                    placeholder="0.00"
                    min={0}
                    max={99999}
                    precision={2}
                    className={styles.fixedInput}
                    addonAfter="元"
                  />
                </Form.Item>
                <Form.Item name="originalPrice" label="原价（选填）">
                  <InputNumber
                    placeholder="0.00"
                    min={0}
                    max={99999}
                    precision={2}
                    className={styles.fixedInput}
                    addonAfter="元"
                  />
                </Form.Item>
              </div>

              {/* 分类三栏 */}
              <div className={styles.row3}>
                <Form.Item
                  name="categoryId"
                  label="商品分类"
                  rules={[{ required: true, message: '请选择分类' }]}
                >
                  <Select
                    placeholder="选择分类"
                    onChange={(value) => setSelectedCategory(value)}
                    className={styles.fixedInput}
                  >
                    {PRODUCT_CATEGORIES.map((cat) => (
                      <Option key={cat.id} value={cat.id}>
                        <span className={styles.optionRow}>
                          {getCategoryIcon(cat.icon)} {cat.name}
                        </span>
                      </Option>
                    ))}
                  </Select>
                </Form.Item>
                <Form.Item name="subCategory" label="子分类">
                  <Select
                    placeholder="选择子分类"
                    allowClear
                    className={styles.fixedInput}
                  >
                    {getSubCategories().map((sub) => (
                      <Option key={sub} value={sub}>
                        {sub}
                      </Option>
                    ))}
                  </Select>
                </Form.Item>
                <Form.Item
                  name="condition"
                  label="成色"
                  rules={[{ required: true, message: '请选择成色' }]}
                >
                  <Select placeholder="选择成色" className={styles.fixedInput}>
                    {CONDITION_OPTIONS.map((opt) => (
                      <Option key={opt.value} value={opt.value}>
                        <Tag color={opt.color} className={styles.inlineTag}>
                          {opt.label}
                        </Tag>
                      </Option>
                    ))}
                  </Select>
                </Form.Item>
              </div>
            </div>

            {/* 右列：图片 + 选项 + 提交 */}
            <div className={styles.formRight}>
              <Form.Item label="商品图片（最多 6 张）">
                <div className={styles.imageGrid}>
                  {Array.from({ length: 6 }).map((_, index) => {
                    const url = images[index];
                    if (url) {
                      return (
                        <div key={index} className={styles.imageCell}>
                          <img src={url} className={styles.imagePreview} alt="" />
                          <button
                            type="button"
                            className={styles.imageRemove}
                            onClick={() =>
                              setImages(images.filter((_, i) => i !== index))
                            }
                          >
                            ×
                          </button>
                          {index === 0 && (
                            <span className={styles.coverBadge}>封面</span>
                          )}
                        </div>
                      );
                    }
                    if (index === images.length) {
                      return (
                        <Upload
                          key={index}
                          customRequest={uploadRequest}
                          showUploadList={false}
                          listType="picture-card"
                        >
                          <div className={styles.imageUploadCell}>
                            <PlusOutlined />
                            <span className={styles.uploadLabel}>上传</span>
                          </div>
                        </Upload>
                      );
                    }
                    return (
                      <div key={index} className={styles.imagePlaceholder} />
                    );
                  })}
                </div>
                <p className={styles.imageTip}>
                  <CameraOutlined /> 第一张为主图，最多 6 张
                </p>
              </Form.Item>

              <Form.Item
                name="location"
                label="交易地点"
                rules={[{ required: true, message: '请选择交易地点' }]}
              >
                <Select placeholder="选择校园交易地点" className={styles.fixedInput}>
                  {CAMPUS_LOCATIONS.map((loc) => (
                    <Option key={loc} value={loc}>
                      {loc}
                    </Option>
                  ))}
                </Select>
              </Form.Item>

              <Form.Item name="isNegotiable" label="议价">
                <Radio.Group buttonStyle="solid">
                  <Radio.Button value={true}>可议价</Radio.Button>
                  <Radio.Button value={false}>一口价</Radio.Button>
                </Radio.Group>
              </Form.Item>

              <Form.Item label="商品标签（最多 5 个）">
                <div className={styles.tagBar}>
                  <Input
                    placeholder="输入标签后按回车"
                    value={tagInput}
                    onChange={(e) => setTagInput(e.target.value)}
                    onPressEnter={handleAddTag}
                    className={styles.tagInput}
                  />
                  <Button onClick={handleAddTag} disabled={!tagInput || serviceTags.length >= 5}>
                    添加
                  </Button>
                </div>
                <div className={styles.tagList}>
                  {serviceTags.map((tag) => (
                    <Tag
                      key={tag}
                      closable
                      onClose={() => handleRemoveTag(tag)}
                      className={styles.tagItem}
                    >
                      {tag}
                    </Tag>
                  ))}
                </div>
              </Form.Item>

              <div className={styles.submitArea}>
                <Button size="large" onClick={() => history.push('/home')}>
                  取消
                </Button>
                <Button
                  type="primary"
                  size="large"
                  loading={loading}
                  onClick={handleSubmitProduct}
                  className={styles.submitBtn}
                >
                  确认发布
                </Button>
              </div>
            </div>
          </Form>
        </div>
      </div>
    );
  }

  // ============== 渲染：服务表单 ==============
  return (
    <div className={styles.formPage}>
      <div className={styles.formHeader}>
        <Button
          type="link"
          className={styles.backBtn}
          onClick={() => switchKind('select')}
        >
          ← 返回选择
        </Button>
        <h2 className={styles.formTitle}>发布服务</h2>
      </div>
      <div className={styles.formCard}>
        <Form
          form={serviceForm}
          layout="vertical"
          className={styles.form}
          initialValues={{ isNegotiable: true, priceUnit: '/次' }}
        >
          {/* 左列：基础信息 */}
          <div className={styles.formLeft}>
            <Form.Item
              name="title"
              label="服务标题"
              rules={[{ required: true, message: '请输入服务标题' }]}
            >
              <Input
                placeholder="简明扼要描述你的服务"
                maxLength={30}
                showCount
                className={styles.fixedInput}
              />
            </Form.Item>

            <Form.Item
              name="description"
              label="服务详情"
              rules={[{ required: true, message: '请输入服务详情' }]}
            >
              <TextArea
                placeholder="详细描述服务内容、时间、范围等"
                maxLength={300}
                showCount
                className={styles.fixedTextarea}
              />
            </Form.Item>

            <Form.Item
              name="serviceType"
              label="服务类型"
              rules={[{ required: true, message: '请选择服务类型' }]}
            >
              <div className={styles.serviceTypeGrid}>
                {SERVICE_TYPES.map((service) => (
                  <label
                    key={service.value}
                    className={`${styles.serviceTypeChip} ${
                      serviceForm.getFieldValue('serviceType') === service.value
                        ? styles.serviceTypeChipActive
                        : ''
                    }`}
                    onClick={() =>
                      serviceForm.setFieldsValue({ serviceType: service.value })
                    }
                  >
                    <span
                      className={styles.serviceTypeDot}
                      style={{ background: service.color }}
                    />
                    {service.label}
                  </label>
                ))}
              </div>
            </Form.Item>

            <div className={styles.row2}>
              <Form.Item
                name="price"
                label="服务价格"
                rules={[{ required: true, message: '请输入价格' }]}
              >
                <InputNumber
                  placeholder="0.00"
                  min={0}
                  max={9999}
                  precision={2}
                  className={styles.fixedInput}
                  addonAfter="元"
                />
              </Form.Item>
              <Form.Item name="priceUnit" label="计价单位">
                <Select className={styles.fixedInput}>
                  <Option value="/次">按次</Option>
                  <Option value="/小时">按小时</Option>
                  <Option value="/件">按件</Option>
                  <Option value="/页">按页</Option>
                </Select>
              </Form.Item>
            </div>
          </div>

          {/* 右列：选项 + 提交 */}
          <div className={styles.formRight}>
            <Form.Item
              name="location"
              label="服务范围"
              rules={[{ required: true, message: '请选择服务范围' }]}
            >
              <Select placeholder="选择服务覆盖区域" className={styles.fixedInput}>
                {CAMPUS_LOCATIONS.map((loc) => (
                  <Option key={loc} value={loc}>
                    {loc}
                  </Option>
                ))}
              </Select>
            </Form.Item>

            <Form.Item name="isNegotiable" label="议价">
              <Radio.Group buttonStyle="solid">
                <Radio.Button value={true}>可议价</Radio.Button>
                <Radio.Button value={false}>固定价</Radio.Button>
              </Radio.Group>
            </Form.Item>

            <Form.Item label="服务标签（最多 5 个）">
              <div className={styles.tagBar}>
                <Input
                  placeholder="输入标签后按回车"
                  value={tagInput}
                  onChange={(e) => setTagInput(e.target.value)}
                  onPressEnter={handleAddTag}
                  className={styles.tagInput}
                />
                <Button onClick={handleAddTag} disabled={!tagInput || serviceTags.length >= 5}>
                  添加
                </Button>
              </div>
              <div className={styles.tagList}>
                {serviceTags.map((tag) => (
                  <Tag
                    key={tag}
                    closable
                    onClose={() => handleRemoveTag(tag)}
                    className={styles.tagItem}
                  >
                    {tag}
                  </Tag>
                ))}
              </div>
            </Form.Item>

            <div className={styles.submitArea}>
              <Button size="large" onClick={() => history.push('/home')}>
                取消
              </Button>
              <Button
                type="primary"
                size="large"
                loading={loading}
                onClick={handleSubmitService}
                className={styles.submitBtn}
              >
                确认发布
              </Button>
            </div>
          </div>
        </Form>
      </div>
    </div>
  );
};

export default PublishPage;