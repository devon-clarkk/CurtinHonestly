import { Directive, ElementRef, HostListener, afterNextRender, inject } from '@angular/core';

/**
 * Grows a textarea to fit what has been typed, up to its CSS `max-height`.
 *
 * The point is that the box stops scrolling: a fixed four-row textarea whose
 * visible height is not a whole number of lines leaves a sliver of a line at
 * the bottom edge, and because the wheel scrolls a fixed number of pixels
 * (~100px in Chrome) rather than a number of lines, no scroll position ever
 * shows that line cleanly. A box that fits its content never scrolls at all.
 *
 * Past `max-height` the textarea does scroll again, so the element it is used
 * on should also size its padding to a whole line (see `.review-textarea`).
 */
@Directive({
  selector: 'textarea[appAutoGrow]',
  standalone: true
})
export class AutoGrowDirective {
  private el = inject<ElementRef<HTMLTextAreaElement>>(ElementRef);

  constructor() {
    // Browser-only, and after the first paint — so a textarea opened with text
    // already in it (editing an existing review) starts at the right height
    // rather than growing on the first keystroke.
    afterNextRender(() => this.resize());
  }

  @HostListener('input')
  onInput(): void {
    this.resize();
  }

  private resize(): void {
    const el = this.el.nativeElement;
    // Collapse first, or the box can only ever grow: scrollHeight of an
    // over-tall element is its own height, not the height of its content.
    el.style.height = 'auto';
    // scrollHeight excludes borders, but box-sizing is border-box globally, so
    // the height we assign includes them. Without this the box sheds its
    // border width on every keystroke.
    const borders = el.offsetHeight - el.clientHeight;
    el.style.height = `${el.scrollHeight + borders}px`;
  }
}
