export type Bbox = {
  x: number;
  y: number;
  w: number;
  h: number;
};

export type VisionCard = {
  name: string;
  bbox: Bbox;
};

export type FrameInput = {
  dataBase64: string;
  mimeType: string;
};

export type DetectOptions = {
  knownCards?: string[];
};

export interface VisionProvider {
  detect(frame: FrameInput, opts?: DetectOptions): Promise<{ cards: VisionCard[] }>;
}
