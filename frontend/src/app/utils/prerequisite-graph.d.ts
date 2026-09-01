/** Types for prerequisite-graph.js. See the header there for why it is JavaScript. */

/** One unit and the raw prerequisite codes the handbook import gave it. */
export interface PrerequisiteEdgeSource {
  code: string;
  prerequisiteCodes: string[];
}

export declare function resolveUnitCode(code: string | null | undefined): string | undefined;

export declare function invertPrerequisiteGraph(
  units: PrerequisiteEdgeSource[],
  catalogueCodes: Iterable<string>
): Record<string, string[]>;
