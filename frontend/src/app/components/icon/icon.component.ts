import { Component, input } from '@angular/core';

/**
 * Inline SVG icons.
 *
 * Every icon is a 24x24 viewBox drawn with `currentColor`, so it inherits the
 * surrounding text colour and scales with font-size (sized in `em`). That keeps
 * icons on the accessible tokens automatically: put an icon inside something
 * coloured with --primary-ink and the icon follows.
 *
 * Icons are decorative here. The interactive controls that use them carry their
 * own aria-label, so the SVGs are aria-hidden and must never be the only way a
 * control is described.
 *
 * Swapping in Icons8 later: export as SVG, strip the width/height attributes,
 * keep viewBox="0 0 24 24", replace any hard-coded stroke/fill with
 * "currentColor", and drop it in as a new @case below. Nothing else changes.
 */
export type IconName =
  | 'chevron-right'
  | 'arrow-left'
  | 'check'
  | 'thumb-up'
  | 'flag'
  | 'eye'
  | 'eye-off';

@Component({
  selector: 'app-icon',
  standalone: true,
  imports: [],
  template: `
    <svg
      viewBox="0 0 24 24"
      [attr.fill]="filled() ? 'currentColor' : 'none'"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      focusable="false">
      @switch (name()) {
        @case ('chevron-right') {
          <polyline points="9 18 15 12 9 6" />
        }
        @case ('arrow-left') {
          <line x1="19" y1="12" x2="5" y2="12" />
          <polyline points="12 19 5 12 12 5" />
        }
        @case ('check') {
          <polyline points="20 6 9 17 4 12" />
        }
        @case ('thumb-up') {
          <path
            d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3z" />
          <path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3" />
        }
        @case ('flag') {
          <path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" />
          <line x1="4" y1="22" x2="4" y2="15" />
        }
        @case ('eye') {
          <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7z" />
          <circle cx="12" cy="12" r="3" />
        }
        @case ('eye-off') {
          <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
          <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
          <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24" />
          <line x1="1" y1="1" x2="23" y2="23" />
        }
      }
    </svg>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
      }

      svg {
        width: 1em;
        height: 1em;
        display: block;
      }
    `,
  ],
})
export class IconComponent {
  name = input.required<IconName>();

  /**
   * Solid rather than outline. Used for on/off states such as a cast like.
   *
   * Accepts undefined/null because the flags driving this are optional fields on
   * the API models (`likedByCurrentUser?: boolean`). Coercing here keeps `!!`
   * out of every call site.
   */
  filled = input(false, {
    transform: (value: boolean | undefined | null) => !!value,
  });
}
