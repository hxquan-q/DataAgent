# DataAgent Chat Output — DESIGN.md

> design-systems skill · 9-segment · derived from WeKnora hierarchy + Arceage Testimonial motion language + ui-ux-pro-max data-dense dashboard  
> Scope: chat / embed output surfaces only (not full product brand)

## 1. Visual Theme

**Philosophy**: Answer first, process secondary, artifacts operable — trust through clarity.  
**Direction**: minimal, data-dense, utilitarian, soft motion  
**Personality**: precise, calm, enterprise-ready, non-theatrical  
**Reference**: WeKnora process tree · Arceage hairline/fade-up · Linear/Claude AI calm surfaces

## 2. Color Palette

### Primary
| Token | HEX | Usage |
|-------|-----|-------|
| --da-primary | #1E40AF | Titles, counts, focus ring |
| --da-accent | #3B82F6 | User bubble, active pulse |
| --da-primary-soft | #EFF6FF | Pending report chip bg |

### Neutral
| Token | HEX | Usage |
|-------|-----|-------|
| --da-ink | #0F172A | Body text |
| --da-muted | #64748B | Meta, captions |
| --da-line | #D9D9D9 | Arceage hairline |
| --da-line-soft | #E8EDF2 | Card borders |
| --da-surface | #FFFFFF | Cards |
| --da-surface-soft | #F8FAFC | Process / empty |

### Semantic
| Token | HEX | Usage |
|-------|-----|-------|
| success | #16A34A | Completed process |
| warning | #B45309 | Large dataset hint |
| danger | #DC2626 | Result error |

## 3. Typography

### Font Stacks
- **Heading/Body**: system-ui, -apple-system, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif  
- **Mono**: ui-monospace, SFMono-Regular, Menlo, Consolas (SQL / numeric cells)

### Scale (chat surfaces)
| Level | Size | Weight | Usage |
|-------|------|--------|-------|
| Welcome title | 22–28px | 600 | ChatWelcome |
| Body | 14px | 400 | Bubbles, markdown |
| Small | 12–13px | 500–600 | Table headers, meta |
| Micro | 11–11.5px | 600 | Badges, pending chip |

**Rules**: letter-spacing -0.01em on answer text; no emoji as icons (mdi only).

## 4. Spacing & Radius

| Token | Value |
|-------|-------|
| message gap | 22px |
| content max | 960px |
| radius bubble | 18px |
| radius card | 12–14px |
| radius control | 6px |
| touch min | 28×28px |

## 5. Elevation

| Level | Shadow | Usage |
|-------|--------|-------|
| 0 | none | Process timeline (dashed) |
| 1 | 0 1px 2px rgba(15,23,42,.04) | AI card |
| 2 | 0 6px 16px rgba(37,99,235,.18) | User bubble |
| 3 | 0 10px 28px rgba(30,64,175,.08) | Report answer |

## 6. Motion

| Name | Spec | Usage |
|------|------|-------|
| da-fade-up | 0.45s ease-out Y12→0 | Welcome |
| da-soft-in | 0.38s ease-out | Message / artifact enter |
| da-scale-x | 0.7s ease-out origin-left | Hairline dividers |
| pendingPulse | 1.2s | Data ready chip |

**Must**: `prefers-reduced-motion: reduce` disables all.  
**Must not**: motion library, token-level typewriter, streaming chart redraw.

## 7. Components (output)

| Component | Role in IA |
|-----------|------------|
| ChatWorkflowTimeline | **process** (collapsible, secondary) |
| ChatMarkdownReport / StreamingReport | **answer** (primary) |
| ChatResultSet | **artifact** (operable table) |
| ChatWelcome | empty state (Arceage reveal) |
| embed pipeline + artifact | external integration mirror |

### ChatResultSet states
error · parse-fail · zero-row · table · large-set warn · pending-report

## 8. Accessibility

- Contrast ≥ 4.5:1 body  
- Focus-visible rings on buttons  
- role=alert on errors; role=status on empty/pending  
- No color-only status (icons + text)  
- Sticky thead keeps context on scroll

## 9. Do / Don't

**Do**
- Answer above process; fold completed steps  
- Share ChatResultSet between main chat and embed  
- CSS-only motion with reduced-motion  
- Measure usefulness (feedback loop / η₇)

**Don't**
- Introduce WeKnora displayType matrix before textType fails  
- Dual auto-scroll answer + table  
- Emoji icons / decorative-only animation  
- Framer Motion for this surface
