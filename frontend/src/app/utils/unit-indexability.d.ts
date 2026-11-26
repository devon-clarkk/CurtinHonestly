/** Types for unit-indexability.js. See the header there for why it is JavaScript. */

export declare const MIN_REVIEWS_TO_INDEX: number;
export declare const INDEXABLE_ROBOTS: string;
export declare const UNREVIEWED_ROBOTS: string;

/** True once a unit has enough published reviews to be offered for indexing. */
export declare function isUnitIndexable(numberOfReviews: number | null | undefined): boolean;

/** The robots meta content for a unit page with this many reviews. */
export declare function unitRobotsDirective(numberOfReviews: number | null | undefined): string;
