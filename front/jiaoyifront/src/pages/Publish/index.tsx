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
  ShoppingCartOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  Button,
  Card,
  Divider,
  Form,
  Input,
  InputNumber,
  message,
  Radio,
  Select,
  Tag,
  Upload,
} from 'antd';
import { useState } from 'react';
import styles from './index.less';
const { TextArea } = Input;
const { Option } = Select;

const PublishPage: React.FC = () => {
  const [form] = Form.useForm();
  const [serviceForm] = Form.useForm();
  console.log('[Publish] serviceForm 实例:', serviceForm);
  const [loading, setLoading] = useState(false);
  const [publishType, setPublishType] = useState<'product' | 'service'>(
    'product',
  );
  const [images, setImages] = useState<string[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [serviceTags, setServiceTags] = useState<string[]>([]);
  const [tagInput, setTagInput] = useState('');

  const handleImageChange = ({ fileList }: any) => {
    const urls = fileList
      .filter((file: any) => file.status === 'done')
      .map((file: any) => file.response?.data || file.response?.url || file.url)
      .filter(Boolean);
    setImages(urls);
  };

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

  const handleAddTag = () => {
    if (tagInput && serviceTags.length < 5) {
      setServiceTags([...serviceTags, tagInput]);
      setTagInput('');
    }
  };

  const handleRemoveTag = (tag: string) => {
    setServiceTags(serviceTags.filter((t) => t !== tag));
  };

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

      console.log('[发布商品] 表单原始值:', JSON.stringify(values));

      const payload = {
        title: (values.title || '').trim(),
        description: (values.description || '').trim(),
        price: values.price,
        originalPrice: values.originalPrice,
        images: images,
        categoryId: values.categoryId,
        subCategory: values.subCategory,
        condition: values.condition,
        location: values.location,
        isNegotiable: values.isNegotiable ? 1 : 0,
        tags: serviceTags,
      };
      console.log('[发布商品] 请求数据:', JSON.stringify(payload));

      await createProduct(payload);

      message.success('商品发布成功！');
      history.push('/my-publish');
    } catch (error: any) {
      console.log(
        '[handleSubmitProduct] catch error:',
        error?.message,
        error?.stack,
      );
      if (error?.response?.data?.message) {
        message.error(error.response.data.message);
      } else {
        message.error(error?.message || '发布失败，请稍后重试');
      }
    } finally {
      console.log('[handleSubmitProduct] finally, loading=false');
      setLoading(false);
    }
  };

  const handleSubmitService = async () => {
    console.log('[handleSubmitService] 开始执行');
    console.log(
      '[handleSubmitService] 当前 serviceForm 所有字段值:',
      JSON.stringify(serviceForm.getFieldsValue(true)),
    );
    try {
      setLoading(true);
      console.log('[handleSubmitService] loading=true');

      console.log('[handleSubmitService] 开始 validateFields');
      let values;
      try {
        values = await serviceForm.validateFields();
        console.log(
          '[handleSubmitService] validateFields 成功, values =',
          JSON.stringify(values),
        );
        console.log(
          '[handleSubmitService] title 原始值:',
          values.title,
          '| typeof:',
          typeof values.title,
        );
      } catch (error: any) {
        console.log(
          '[handleSubmitService] validateFields 验证失败, errorFields:',
          JSON.stringify(error?.errorFields),
        );
        const errorFields = error?.errorFields;
        if (errorFields && errorFields.length > 0) {
          message.error(errorFields[0]?.errors?.[0] || '请完善表单信息');
        }
        return;
      }

      console.log(
        '[handleSubmitService] getFieldsValue:',
        JSON.stringify(serviceForm.getFieldsValue()),
      );

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
      console.log('[发布服务] 最终 payload:', JSON.stringify(payload));

      await createService(payload);

      message.success('服务发布成功！');
      history.push('/my-publish');
    } catch (error: any) {
      console.log(
        '[handleSubmitService] catch error:',
        error?.message,
        error?.stack,
      );
      if (error?.response?.data?.message) {
        message.error(error.response.data.message);
      } else {
        message.error(error?.message || '发布失败，请稍后重试');
      }
    } finally {
      console.log('[handleSubmitService] finally, loading=false');
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

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h1 className={styles.title}>发布宝贝</h1>
        <p className={styles.subtitle}>选择要发布的类型，开始交易</p>
      </div>

      <Radio.Group
        value={publishType}
        onChange={(e) => setPublishType(e.target.value)}
        className={styles.publishTypeGroup}
        buttonStyle="solid"
      >
        <Radio.Button value="product">发布商品</Radio.Button>
        <Radio.Button value="service">发布服务</Radio.Button>
      </Radio.Group>

      {publishType === 'product' ? (
        <Card key="product" className={styles.formCard}>
          <Form
            form={form}
            layout="vertical"
            className={styles.form}
            initialValues={{ isNegotiable: true, condition: '轻微使用' }}
          >
            <Form.Item
              name="title"
              label="商品名称"
              rules={[{ required: true, message: '请输入商品名称' }]}
            >
              <Input
                placeholder="简洁明了，一眼看出卖点"
                maxLength={50}
                showCount
              />
            </Form.Item>

            <Form.Item
              name="description"
              label="商品描述"
              rules={[{ required: true, message: '请输入商品描述' }]}
            >
              <TextArea
                placeholder="详细描述成色、使用情况、转手原因等"
                rows={4}
                maxLength={500}
                showCount
              />
            </Form.Item>

            <div className={styles.formRow}>
              <Form.Item
                name="price"
                label="出售价格"
                rules={[{ required: true, message: '请输入价格' }]}
                className={styles.priceInput}
              >
                <InputNumber
                  placeholder="0.00"
                  min={0}
                  max={99999}
                  precision={2}
                  style={{ width: '100%' }}
                  addonAfter="元"
                />
              </Form.Item>

              <Form.Item
                name="originalPrice"
                label="原价（选填）"
                className={styles.priceInput}
              >
                <InputNumber
                  placeholder="0.00"
                  min={0}
                  max={99999}
                  precision={2}
                  style={{ width: '100%' }}
                  addonAfter="元"
                />
              </Form.Item>
            </div>

            <div className={styles.formRow}>
              <Form.Item
                name="categoryId"
                label="商品分类"
                rules={[{ required: true, message: '请选择分类' }]}
                className={styles.categorySelect}
              >
                <Select
                  placeholder="选择分类"
                  onChange={(value) => setSelectedCategory(value)}
                >
                  {PRODUCT_CATEGORIES.map((cat) => (
                    <Option key={cat.id} value={cat.id}>
                      <span
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 8,
                        }}
                      >
                        {getCategoryIcon(cat.icon)} {cat.name}
                      </span>
                    </Option>
                  ))}
                </Select>
              </Form.Item>

              <Form.Item
                name="subCategory"
                label="子分类"
                className={styles.categorySelect}
              >
                <Select placeholder="选择子分类" allowClear>
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
                className={styles.conditionSelect}
              >
                <Select placeholder="选择成色">
                  {CONDITION_OPTIONS.map((opt) => (
                    <Option key={opt.value} value={opt.value}>
                      <Tag color={opt.color}>{opt.label}</Tag>
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </div>

            <Form.Item label="商品图片">
              <div className={styles.imageUpload}>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                  {images.map((url, index) => (
                    <div
                      key={index}
                      style={{ position: 'relative', width: 104, height: 104 }}
                    >
                      <img
                        src={url}
                        style={{
                          width: 104,
                          height: 104,
                          borderRadius: 8,
                          objectFit: 'cover',
                        }}
                        alt=""
                      />
                      <Button
                        type="text"
                        size="small"
                        danger
                        style={{
                          position: 'absolute',
                          top: 4,
                          right: 4,
                          padding: 2,
                          minWidth: 0,
                          width: 20,
                          height: 20,
                        }}
                        onClick={() =>
                          setImages(images.filter((_, i) => i !== index))
                        }
                      >
                        ×
                      </Button>
                    </div>
                  ))}
                  {images.length < 9 && (
                    <Upload
                      customRequest={uploadRequest}
                      showUploadList={false}
                      beforeUpload={(file) => {
                        if (images.length >= 9) return Upload.LIST_IGNORE;
                        return true;
                      }}
                    >
                      <div
                        style={{
                          width: 104,
                          height: 104,
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          justifyContent: 'center',
                          border: '1px dashed #d9d9d9',
                          borderRadius: 8,
                          cursor: 'pointer',
                        }}
                      >
                        <PlusOutlined />
                        <div style={{ marginTop: 8 }}>上传图片</div>
                      </div>
                    </Upload>
                  )}
                </div>
                <p className={styles.imageTip}>
                  <CameraOutlined /> 最多上传9张，建议第一张为主图
                </p>
              </div>
            </Form.Item>

            <Form.Item
              name="location"
              label="交易地点"
              rules={[{ required: true, message: '请选择交易地点' }]}
            >
              <Select placeholder="选择校园交易地点">
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

            <Form.Item label="商品标签">
              <div className={styles.tagInput}>
                <Input
                  placeholder="输入标签后按回车添加"
                  value={tagInput}
                  onChange={(e) => setTagInput(e.target.value)}
                  onPressEnter={handleAddTag}
                  style={{ width: 200 }}
                />
                <Button
                  onClick={handleAddTag}
                  disabled={!tagInput || serviceTags.length >= 5}
                >
                  添加
                </Button>
              </div>
              <div className={styles.tags}>
                {serviceTags.map((tag) => (
                  <Tag
                    key={tag}
                    closable
                    onClose={() => handleRemoveTag(tag)}
                    className={styles.tag}
                  >
                    {tag}
                  </Tag>
                ))}
              </div>
            </Form.Item>

            <Divider />

            <Form.Item style={{ marginBottom: 0 }}>
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
            </Form.Item>
          </Form>
        </Card>
      ) : (
        <Card key="service" className={styles.formCard}>
          <Form
            form={serviceForm}
            layout="vertical"
            className={styles.form}
            initialValues={{ isNegotiable: true, priceUnit: '/次' }}
          >
            <Form.Item
              name="title"
              label="服务标题"
              rules={[{ required: true, message: '请输入服务标题' }]}
            >
              <Input
                placeholder="简明扼要描述你的服务"
                maxLength={30}
                showCount
              />
            </Form.Item>

            <Form.Item
              name="description"
              label="服务详情"
              rules={[{ required: true, message: '请输入服务详情' }]}
            >
              <TextArea
                placeholder="详细描述服务内容、时间、范围等"
                rows={4}
                maxLength={300}
                showCount
              />
            </Form.Item>

            <Form.Item
              name="serviceType"
              label="服务类型"
              rules={[{ required: true, message: '请选择服务类型' }]}
            >
              <Radio.Group className={styles.serviceTypeGroup}>
                {SERVICE_TYPES.map((service) => (
                  <Radio.Button
                    key={service.value}
                    value={service.value}
                    className={styles.serviceTypeBtn}
                  >
                    <span style={{ color: service.color }}>
                      {service.label}
                    </span>
                  </Radio.Button>
                ))}
              </Radio.Group>
            </Form.Item>

            <div className={styles.formRow}>
              <Form.Item
                name="price"
                label="服务价格"
                rules={[{ required: true, message: '请输入价格' }]}
                className={styles.priceInput}
              >
                <InputNumber
                  placeholder="0.00"
                  min={0}
                  max={9999}
                  precision={2}
                  style={{ width: '100%' }}
                  addonAfter="元"
                />
              </Form.Item>

              <Form.Item
                name="priceUnit"
                label="计价单位"
                className={styles.priceUnit}
              >
                <Select>
                  <Option value="/次">按次</Option>
                  <Option value="/小时">按小时</Option>
                  <Option value="/件">按件</Option>
                  <Option value="/页">按页</Option>
                </Select>
              </Form.Item>
            </div>

            <Form.Item
              name="location"
              label="服务范围"
              rules={[{ required: true, message: '请选择服务范围' }]}
            >
              <Select placeholder="选择服务覆盖区域">
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

            <Form.Item label="服务标签">
              <div className={styles.tagInput}>
                <Input
                  placeholder="输入标签后按回车添加"
                  value={tagInput}
                  onChange={(e) => setTagInput(e.target.value)}
                  onPressEnter={handleAddTag}
                  style={{ width: 200 }}
                />
                <Button
                  onClick={handleAddTag}
                  disabled={!tagInput || serviceTags.length >= 5}
                >
                  添加
                </Button>
              </div>
              <div className={styles.tags}>
                {serviceTags.map((tag) => (
                  <Tag
                    key={tag}
                    closable
                    onClose={() => handleRemoveTag(tag)}
                    className={styles.tag}
                  >
                    {tag}
                  </Tag>
                ))}
              </div>
            </Form.Item>

            <Divider />

            <Form.Item style={{ marginBottom: 0 }}>
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
            </Form.Item>
          </Form>
        </Card>
      )}
    </div>
  );
};

export default PublishPage;
