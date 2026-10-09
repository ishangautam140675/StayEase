# Permanent UI Design Rules for StayEase

Treat the complete UI design specification as the single source of truth for the visual design of this project.

For every current and future phase:
- Follow the specified cobalt color palette, CSS tokens, typography, spacing, buttons, forms, cards, navigation, status badges, and responsive behavior.
- Reuse shared styles and Thymeleaf fragments instead of creating inconsistent page-specific designs.
- Apply the design system to every new or modified page, including authentication, room browsing, booking forms, customer dashboards, and admin dashboards.
- Keep the admin and customer experiences visually distinct where specified, while maintaining a consistent overall brand.
- Do not invent alternative brand colors, introduce a competing theme, or arbitrarily change the specified design tokens.
- Preserve all existing functionality, routing, security rules, validation, and business logic.
- Respect the file list and scope of the active phase. Do not implement features from later phases early.
- Do not modify unrelated files just to apply styling.
- Run `./mvnw test` after each phase and fix regressions before reporting completion.
- If a visual requirement conflicts with a phase's technical constraints, explain the conflict and use the smallest compatible change rather than silently ignoring either requirement.

This specification remains in effect throughout the project. Follow it whenever creating or modifying UI components, templates, or styles.
