import type { SearchImageData } from '../../types/search';
import { ImageViewer } from '../common/ImageViewer';

export function ImageZoomModal({ image, onClose }: { image: SearchImageData; onClose: () => void }) {
  return <ImageViewer src={image.imageUrl} alt="Ảnh phóng to" onClose={onClose} />;
}
