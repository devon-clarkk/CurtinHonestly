/** Types for share-image.js. See the header there for why it is JavaScript. */

/** The image URL for a page and the card type that fits its shape. */
export interface ShareCardMeta {
  image: string;
  card: 'summary' | 'summary_large_image';
}

export declare const SHARE_IMAGE_DIR: string;
export declare const FALLBACK_IMAGE_PATH: string;
export declare const INFO_CARD_NAMES: string[];

/** Site-root-relative card path, or null when no card is generated for it. */
export declare function unitShareImagePath(code: string | null | undefined): string | null;
export declare function facultyShareImagePath(slug: string | null | undefined): string | null;
export declare function infoShareImagePath(name: string | null | undefined): string | null;

export declare function shareCardMeta(
  imagePath: string | null,
  siteUrl: string
): ShareCardMeta;
