import React, { useEffect, useRef, useState } from 'react';

interface ProductImageProps {
  src?: string | string[] | null;
  alt?: string;
  width?: number | string;
  height?: number | string;
  style?: React.CSSProperties;
  className?: string;
  onClick?: () => void;
}

function toValidSrc(val?: string | string[] | null): string {
  if (!val) return '';
  if (Array.isArray(val)) {
    if (val.length === 0) return '';
    const first = val[0];
    return typeof first === 'string' && first.trim() ? first : '';
  }
  if (typeof val === 'string' && val.trim()) return val;
  return '';
}

const ProductImage: React.FC<ProductImageProps> = ({
  src,
  alt = '',
  width,
  height,
  style,
  className,
  onClick,
}) => {
  const [imgSrc, setImgSrc] = useState(() => toValidSrc(src));
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    const next = toValidSrc(src);
    if (next !== imgSrc) {
      setImgSrc(next);
      setLoaded(false);
      setError(false);
    }
  }, [src]);

  const showText = !imgSrc || error;

  const handleError = () => setError(true);
  const imgRef = useRef<HTMLImageElement>(null);

  const handleLoad = () => setLoaded(true);

  useEffect(() => {
    if (imgRef.current?.complete) setLoaded(true);
  }, [imgSrc]);

  return (
    <div
      className={className}
      onClick={onClick}
      style={{
        ...style,
        width: width ?? style?.width,
        height: height ?? style?.height,
        display: 'block',
        overflow: 'hidden',
        cursor: onClick ? 'pointer' : 'default',
        background: '#f0f0f0',
        position: 'relative',
      }}
    >
      {showText ? (
        <div
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '8px 12px',
            boxSizing: 'border-box',
            wordBreak: 'break-word',
            textAlign: 'center',
            fontSize: alt
              ? Math.max(12, Math.min(16, 240 / (alt.length + 1)))
              : 14,
            color: '#aaa',
            lineHeight: 1.4,
          }}
        >
          {alt}
        </div>
      ) : (
        <img
          ref={imgRef}
          src={imgSrc}
          alt={alt}
          decoding="async"
          onError={handleError}
          onLoad={handleLoad}
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            objectFit: 'cover',
            opacity: loaded ? 1 : 0,
            transition: 'opacity 0.3s ease',
          }}
        />
      )}
    </div>
  );
};

export default ProductImage;
