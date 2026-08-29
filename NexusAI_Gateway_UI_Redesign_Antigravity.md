# NexusAI Gateway --- Premium SaaS UI Redesign Specification

## 0. Mission

Redesign the existing NexusAI Gateway frontend into a **premium,
production-grade AI infrastructure SaaS dashboard**.

The current application already has working pages, navigation, backend
integration, data, API actions, routing, telemetry, cache, RAG, model
health, logs, agent pipelines, and benchmarking. **Do not rebuild the
product from scratch and do not break existing functionality.**

Your job is primarily to upgrade the **visual system, information
hierarchy, interaction design, responsiveness, and light/dark themes**
while preserving the existing business logic and API contracts.

The final result should feel like a serious developer infrastructure
product comparable in polish to modern products such as Linear, Vercel,
Stripe, Cloudflare, Datadog, and high-end AI developer platforms --- but
with its own NexusAI identity.

------------------------------------------------------------------------

# 1. First: Audit Before Editing

Before changing code:

1.  Inspect the complete existing frontend structure.
2.  Identify:
    -   global layout
    -   sidebar
    -   header/top bar
    -   routing
    -   reusable components
    -   cards
    -   tables
    -   buttons
    -   badges
    -   forms
    -   modals
    -   charts
    -   empty states
    -   loading states
    -   error states
    -   theme implementation
3.  Identify which UI elements are connected to real backend/API state.
4.  Preserve all existing functionality.
5.  Do not replace working API calls with mocked data.
6.  Do not rename API fields, endpoints, routes, or backend contracts
    unless absolutely necessary.
7.  Reuse existing components where possible, but refactor them if they
    are visually inconsistent.

**Important:** This is a UI/UX modernization task, not a backend
rewrite.

------------------------------------------------------------------------

# 2. Current Problems to Fix

The existing UI has a solid information architecture but currently feels
too:

-   generic
-   flat
-   white/card-heavy
-   spacious without enough hierarchy
-   visually repetitive
-   dashboard-template-like
-   dependent on borders for separation
-   inconsistent in visual emphasis
-   weak in dark mode
-   weak in empty/loading states
-   lacking a strong premium product identity

Specific problems visible across the current screens:

### Sidebar

Current sidebar is functional but visually basic.

Improve it into a refined product navigation system with:

-   stronger active-state treatment
-   clearer section hierarchy
-   compact icons
-   subtle hover states
-   workspace/product identity
-   optional collapse behavior
-   excellent dark-mode treatment
-   better bottom account/workspace area

### Header

The top header should become more useful and polished.

Include:

-   breadcrumb/page context where useful
-   page title
-   concise description
-   environment/status indicator
-   workspace/user control
-   theme switcher
-   optional command/search affordance
-   responsive mobile behavior

Avoid excessive empty horizontal space.

------------------------------------------------------------------------

# 3. Design Direction

## Product personality

NexusAI should communicate:

**AI infrastructure + developer platform + enterprise reliability**

Visual keywords:

-   precise
-   intelligent
-   technical
-   calm
-   premium
-   trustworthy
-   modern
-   data-driven
-   developer-first

Do NOT make it look like:

-   a crypto dashboard
-   a gaming UI
-   a neon AI landing page
-   a generic Bootstrap admin panel
-   an overly colorful analytics template

------------------------------------------------------------------------

# 4. Design System

Create a centralized design-token system.

Do not hardcode random colors page-by-page.

Use CSS variables / theme tokens such as:

``` css
--background
--surface
--surface-elevated
--surface-subtle
--border
--border-strong
--text-primary
--text-secondary
--text-muted
--accent
--accent-hover
--success
--warning
--danger
--info
--focus
```

Every reusable component must consume these tokens.

------------------------------------------------------------------------

# 5. Light Theme

The light theme should NOT be pure white everywhere.

Use a sophisticated layered neutral system:

### Main background

Very light cool-neutral background.

### Cards

Near-white surfaces with subtle elevation.

### Borders

Extremely subtle cool-gray borders.

### Primary accent

Use NexusAI's existing teal/cyan identity, but make it more refined and
restrained.

### Secondary accent

Use a controlled indigo/violet only for AI/model-related highlights.

### Status colors

-   Success → refined green
-   Warning → amber
-   Error → red
-   Info → blue/cyan

Avoid excessive gradients.

Use gradients only for special hero/visual elements.

------------------------------------------------------------------------

# 6. Dark Theme

Dark mode must be a **first-class design**, not an inverted light mode.

Target visual direction:

-   deep charcoal/navy background
-   slightly lighter elevated surfaces
-   subtle borders
-   high readability
-   restrained teal/cyan accent
-   indigo/violet used selectively
-   muted secondary text
-   no huge black voids
-   no glowing neon overload

Suggested conceptual hierarchy:

``` text
App background
    ↓
Sidebar
    ↓
Primary surface
    ↓
Elevated card
    ↓
Interactive/selected surface
```

Use subtle shadows/glows only where they improve hierarchy.

Do not make every card glow.

------------------------------------------------------------------------

# 7. Typography

Use a modern UI font.

Preferred hierarchy:

-   Inter / Geist / equivalent modern UI sans for interface
-   Monospace font for:
    -   model IDs
    -   API keys
    -   endpoints
    -   logs
    -   code
    -   latency values where appropriate

Typography should have clear hierarchy:

``` text
Page title
Section title
Card title
Body
Secondary text
Metadata
Micro-label
```

Avoid tiny text everywhere.

Increase readability of secondary descriptions while keeping the
interface dense enough for developers.

------------------------------------------------------------------------

# 8. Spacing & Layout

Adopt a consistent spacing scale.

Use:

-   4
-   8
-   12
-   16
-   20
-   24
-   32
-   40

Do not randomly use different gaps.

Desktop:

-   optimized for 1280--1600px screens
-   content should not feel stretched
-   use max-width/content containers where appropriate

Laptop:

-   preserve density
-   cards should adapt rather than collapse awkwardly

Mobile:

-   responsive sidebar/drawer
-   stacked cards
-   horizontal table scrolling
-   controls remain usable
-   no clipped content

------------------------------------------------------------------------

# 9. Global UI Components

Build/refine a consistent component system.

## Buttons

Variants:

-   Primary
-   Secondary
-   Ghost
-   Destructive
-   Icon
-   Loading

Primary CTA should be visually strong but not oversized.

Use consistent:

-   radius
-   height
-   typography
-   hover
-   focus
-   disabled
-   loading

------------------------------------------------------------------------

## Cards

Avoid making every section look like a floating white rectangle.

Use three levels:

1.  Surface section
2.  Elevated card
3.  Interactive card

Some content should be separated by spacing/dividers rather than cards.

------------------------------------------------------------------------

## Badges

Create semantic badges:

-   Operational
-   Healthy
-   Rate Limited
-   Degraded
-   Unreachable
-   Success
-   Fallback
-   Production
-   Development

Badges should be compact and readable.

------------------------------------------------------------------------

## Tables

Tables are important to NexusAI.

Create a premium developer-console table style:

-   sticky header where appropriate
-   subtle row separators
-   hover row state
-   compact but readable density
-   monospace technical fields
-   aligned numeric values
-   semantic status badges
-   action buttons
-   responsive horizontal scrolling

Do not use giant row heights.

------------------------------------------------------------------------

# 10. Global Theme Switcher

Implement a real theme switcher.

Options:

-   Light
-   Dark
-   System

Requirements:

-   persist user preference
-   update the entire application instantly
-   no flash/flicker if possible
-   charts must adapt
-   code/log areas must adapt
-   modals/dropdowns must adapt
-   sidebar must adapt
-   empty states must adapt

Do not create separate duplicated page implementations for light/dark.

Use theme tokens.

------------------------------------------------------------------------

# 11. Analytics & Telemetry Page

Current route:

`/app/analytics`

Redesign this page as the main observability command center.

## Top KPI area

Keep the important metrics:

-   Total Cost
-   Average Latency
-   Failure Rate
-   Zero-Trust Shield

But make them more sophisticated.

Each KPI card should include:

-   label
-   primary metric
-   trend
-   comparison period
-   tiny sparkline
-   optional status/context
-   icon

Avoid four identical cards.

Give each metric a visual identity.

------------------------------------------------------------------------

## Real-Time Traffic Flow

This should become one of the strongest sections.

Instead of a static box:

Create a visual pipeline:

``` text
Client Applications
        ↓
NexusAI Control Plane
        ↓
Routing / Policy
        ↓
Selected Model
```

Show live traffic moving through the system when real events exist.

Include:

-   request count
-   fallback count
-   routing decision
-   active providers
-   latency
-   cache hit/miss
-   status

If there is no live event:

Show a polished empty state:

> Waiting for live traffic

with an animated but subtle connection indicator.

Do not fake real traffic.

------------------------------------------------------------------------

## Model SLA & Latency Benchmark

Turn this into a proper benchmark visualization.

Include:

-   benchmark controls
-   latency comparison
-   model/provider comparison
-   success rate
-   fallback rate
-   cache impact

Use charts that are readable in both themes.

------------------------------------------------------------------------

## Model Arm Health

Improve current progress-bar list into a compact health monitoring
panel.

Show:

-   model
-   provider
-   health score
-   latency
-   recent status
-   trend

Use semantic status instead of showing meaningless repeated 50% values
if backend data is unavailable.

If data is unavailable, clearly communicate it instead of pretending.

------------------------------------------------------------------------

## Budget Governance

Make this visually stronger.

Include:

-   daily spend
-   daily limit
-   monthly spend
-   monthly limit
-   utilization percentage
-   remaining budget

Use a progress visualization.

------------------------------------------------------------------------

## Zero-Trust Security

Show:

-   credential encryption
-   API key hashing
-   PII redaction
-   audit trail
-   security status

Use a compact security-health design.

------------------------------------------------------------------------

# 12. Prompt Cache & Optimization Studio

Current route:

`/app/cache`

Redesign into a real optimization workspace.

## KPI section

Show:

-   Total Cost Saved
-   Latency Saved
-   Cache Hit Ratio
-   Total Cache Hits

If values are zero, do not make the page look broken.

Use a useful zero-data state explaining:

> No cache activity yet

and show how activity will populate the metrics.

------------------------------------------------------------------------

## Semantic Caching Controls

Improve sliders.

Each setting should show:

-   name
-   current value
-   explanation
-   effect
-   reset/default affordance

Example:

``` text
Similarity threshold
95%
Higher = stricter matching
```

Use a modern slider design.

------------------------------------------------------------------------

## Redis Cache Engine

Make it feel like infrastructure status.

Show:

-   engine status
-   hits
-   misses
-   latency saved
-   hit ratio
-   last activity

Include a small visual status indicator.

------------------------------------------------------------------------

## Flush Prompt Cache

This must use a confirmation modal.

Explain consequences clearly.

Do not flush immediately without confirmation.

------------------------------------------------------------------------

# 13. RAG Knowledge Studio

Current route:

`/app/rag`

This should feel like a developer knowledge-management console.

## Layout

Two-column desktop layout:

### Left

Knowledge index.

Show:

-   total chunks
-   search
-   sync
-   filters
-   document cards/list
-   metadata
-   delete actions

### Right

Ingest panel.

Improve:

-   document name
-   content input
-   drag-and-drop support if compatible with current functionality
-   ingest button
-   character/token count
-   validation

------------------------------------------------------------------------

## Search

Search should feel like semantic retrieval.

Include:

-   search input
-   search state
-   result relevance
-   source document
-   chunk preview

If no query:

Show an intentional empty state.

If no results:

Show:

> No relevant knowledge found

not a blank area.

------------------------------------------------------------------------

# 14. API Keys

Current route:

`/app/keys`

This page should feel like a secure developer credential manager.

## Key list

Show:

-   key name
-   environment
-   tenant
-   masked key
-   created date
-   budget
-   rate limit
-   status

Use clear production/development badges.

------------------------------------------------------------------------

## Key actions

Actions:

-   Copy
-   Revoke
-   View details

Revoke must require confirmation.

Never expose full secrets after initial generation.

------------------------------------------------------------------------

## Generate API Key

Use a polished modal.

Flow:

1.  Key name
2.  Environment
3.  Budget
4.  Rate limit
5.  Create
6.  Show secret once
7.  Copy button
8.  Warning that it cannot be retrieved later

------------------------------------------------------------------------

## Security explanation

The existing authentication explanation should become a compact security
panel rather than a large plain text block.

Use code styling for:

-   `X-API-Key`
-   `Authorization: Bearer`
-   `GatewaySecurityFilter`
-   `SecurityConfig.java`

------------------------------------------------------------------------

# 15. Model Health Governance

Current route:

`/app/health`

This should look like a real model operations center.

## Header controls

-   Active Routing Models
-   All Provider Candidate Models
-   Run System Health Scan

Make the selected tab visually obvious.

------------------------------------------------------------------------

## KPI row

Show:

-   Total Registered
-   Healthy
-   Rate Limited
-   Degraded
-   Unreachable
-   Average Latency

Do not use misleading labels such as:

`DEGRADED / 429`

if the actual state is rate limited.

Use separate semantic statuses.

------------------------------------------------------------------------

## Model table

Columns:

-   Provider
-   Model ID
-   Status
-   Routing
-   Last Tested
-   Latency
-   Diagnostic
-   Actions

Improve readability with:

-   provider icons/logos where legally/technically available
-   model ID monospace
-   semantic badges
-   latency visualization
-   concise diagnostic messages

------------------------------------------------------------------------

## Health Scan

When scanning:

-   show progress
-   show number tested
-   show current model
-   show success/failure counts

After scan:

-   update metrics
-   update table
-   show timestamp

Do not freeze the UI.

------------------------------------------------------------------------

# 16. Logs

Current route:

`/app/logs`

This is a high-value developer page.

Make it feel like a production observability log explorer.

## Toolbar

Include:

-   search
-   provider filter
-   model filter
-   status filter
-   time range
-   refresh
-   optional export if already supported

------------------------------------------------------------------------

## Table

Use columns:

-   timestamp
-   tenant
-   provider
-   model
-   tokens
-   cost
-   latency
-   status

Technical values should use monospace where useful.

Latency should have semantic visual treatment.

------------------------------------------------------------------------

## Log details

Clicking a log should open a detail drawer/modal.

Show:

-   request metadata
-   routing decision
-   model
-   provider
-   token usage
-   cost
-   latency
-   cache state
-   fallback state
-   errors if any

Never expose sensitive secrets.

------------------------------------------------------------------------

# 17. Agent Pipelines

Current route:

`/app/agents`

This should feel like an orchestration graph, not a list of cards.

Represent:

``` text
ContextAgent
      ↓
IntentAgent
      ↓
PolicyAgent
      ↓
RoutingAgent
      ↓
QualityAgent
      ↓
FeedbackAgent
```

Show dependencies visually.

Each agent node should contain:

-   name
-   status
-   inputs
-   outputs
-   dependency
-   execution state

------------------------------------------------------------------------

## Execution Console

Make the console look like a modern developer tool.

Include:

-   prompt editor
-   run button
-   request state
-   execution timeline
-   final response
-   per-agent results
-   latency
-   model selection
-   errors

When executing, visually highlight the active agent.

------------------------------------------------------------------------

# 18. Benchmarking Labs

Current route:

`/app/labs`

This page currently feels too empty.

Turn it into an actual experimentation workspace.

## Benchmark suites

Create refined cards for:

### Latency Benchmark

Explain:

> Send real requests through the gateway and compare end-to-end latency.

Show:

-   number of requests
-   models/providers tested
-   expected cost
-   warning

### Routing Convergence Test

Show:

-   request count
-   bandit/routing strategy
-   distribution
-   expected result

------------------------------------------------------------------------

## Live Output

Replace the large grey empty rectangle with a professional
terminal/result panel.

States:

### Before execution

``` text
Ready to run benchmark
Select a suite to begin.
```

### Running

Show live progress.

### Completed

Show:

-   summary
-   charts
-   provider comparison
-   latency
-   success rate
-   routing distribution

------------------------------------------------------------------------

# 19. Dashboard

Even though screenshots focus on the other pages, apply the same
redesign principles to `/app/dashboard`.

The dashboard should answer:

**"Is my AI gateway healthy and is it costing me what I expect?"**

Recommended hierarchy:

1.  Workspace/header
2.  Health summary
3.  Cost + usage
4.  Traffic
5.  Routing performance
6.  Model health
7.  Recent activity
8.  Quick actions

Do not overload it with every metric.

------------------------------------------------------------------------

# 20. Providers / Models / Routing / Sandbox

Apply the same visual system.

## Providers

Make provider configuration easy to scan.

Show:

-   provider
-   connection status
-   configured models
-   latency
-   usage
-   credentials state
-   actions

## Models

Show:

-   provider
-   model ID
-   capabilities
-   status
-   latency
-   pricing where available
-   routing state

## Routing

Make routing rules visually understandable.

Use:

-   rule priority
-   conditions
-   selected models
-   fallback chain
-   budget/latency policy
-   enable/disable state

## Sandbox

Make it feel like an API playground.

Include:

-   request editor
-   headers
-   model selection
-   prompt
-   parameters
-   send
-   response
-   latency
-   tokens
-   cost
-   routing decision

------------------------------------------------------------------------

# 21. Empty States

Never use a blank card or grey rectangle.

Every empty state needs:

-   icon/illustration
-   title
-   explanation
-   optional CTA

Examples:

``` text
No cache activity yet
Cache metrics will appear after the gateway serves cacheable requests.

[View Gateway]
```

``` text
No benchmark results
Run a benchmark to compare model performance.

[Run Benchmark]
```

``` text
No live traffic
Traffic events will appear here when requests reach the gateway.
```

------------------------------------------------------------------------

# 22. Loading States

Use skeleton loaders instead of abrupt blank areas.

Skeletons should match actual component geometry.

Avoid generic spinning loaders everywhere.

Use:

-   skeletons for tables/cards
-   progress for long-running scans
-   subtle inline spinners for buttons
-   streaming indicators for live events

------------------------------------------------------------------------

# 23. Error States

Create consistent error UI.

Errors should explain:

1.  what failed
2.  why it may have failed
3.  what the user can do

Example:

``` text
Unable to load model health

The gateway did not return a health snapshot.

[Retry]
```

Do not show raw stack traces in the main UI.

------------------------------------------------------------------------

# 24. Motion

Use subtle motion.

Good:

-   hover transitions
-   active state transitions
-   card entrance
-   table row highlight
-   progress animation
-   live traffic pulse
-   modal transitions

Avoid:

-   excessive bouncing
-   huge animations
-   distracting glowing effects
-   long page transitions

Animation duration should generally be around 120--250ms for UI
interactions.

Respect `prefers-reduced-motion`.

------------------------------------------------------------------------

# 25. Icons

Use one consistent icon system.

Recommended:

-   Lucide
-   existing icon library if already installed

Do not mix random icon styles.

Icons should communicate meaning, not decoration.

------------------------------------------------------------------------

# 26. Provider Logos

Where provider identity is displayed, use official/provider-appropriate
logos if assets are already available or can be safely referenced.

Providers may include:

-   OpenAI
-   Google Gemini
-   Anthropic
-   Groq
-   local/Ollama

Do not invent logos.

If official logos cannot be used, use clean provider
initials/iconography.

------------------------------------------------------------------------

# 27. Accessibility

Ensure:

-   keyboard navigation
-   visible focus states
-   semantic buttons
-   accessible labels
-   sufficient contrast
-   tooltips for icon-only controls
-   no color-only status communication

Dark mode must meet readable contrast standards.

------------------------------------------------------------------------

# 28. Responsive Behavior

Desktop is the primary environment, but the application must work on:

-   1440px
-   1280px
-   1024px
-   tablet
-   mobile

At smaller widths:

-   sidebar collapses
-   KPI cards stack
-   complex tables become horizontally scrollable
-   two-column sections stack
-   action controls wrap
-   modal widths adapt

Never allow horizontal page overflow.

------------------------------------------------------------------------

# 29. Important Functional Constraints

DO NOT:

-   remove existing routes
-   remove backend functionality
-   replace real API data with fake data
-   hardcode production metrics
-   break authentication
-   expose secrets
-   remove working buttons
-   remove existing filtering
-   remove existing benchmark functionality
-   remove routing functionality
-   remove RAG functionality
-   remove cache functionality
-   remove health checks
-   remove logging
-   remove agent execution

DO:

-   preserve existing state management
-   preserve API calls
-   preserve business logic
-   preserve database behavior
-   improve component architecture
-   improve error handling visually
-   improve loading states
-   improve responsiveness
-   improve accessibility

------------------------------------------------------------------------

# 30. Data Integrity Rule

A premium UI is still bad if it lies.

If a metric is unavailable:

Show:

`—`

or an explicit empty state.

Do not invent:

-   request counts
-   costs
-   latency
-   health scores
-   cache hits
-   model availability

The UI must distinguish between:

-   zero
-   unavailable
-   loading
-   error

These are not the same state.

------------------------------------------------------------------------

# 31. Final Visual Target

The final NexusAI UI should feel like:

> **A serious AI infrastructure control plane used by engineering
> teams.**

It should have:

-   excellent visual hierarchy
-   restrained colors
-   dense but readable information
-   premium tables
-   polished charts
-   meaningful empty states
-   strong dark mode
-   refined light mode
-   subtle motion
-   consistent spacing
-   professional typography
-   clear system status
-   developer-friendly technical details

It should look credible in a:

-   SaaS product demo
-   startup pitch
-   GitHub README screenshot
-   engineering portfolio
-   enterprise architecture presentation
-   product interview

------------------------------------------------------------------------

# 32. Implementation Order

Do the work in this order:

### Phase 1 --- Foundation

1.  Theme tokens
2.  Light theme
3.  Dark theme
4.  Typography
5.  Global spacing
6.  Sidebar
7.  Header
8.  Buttons
9.  Cards
10. Badges
11. Tables
12. Modals
13. Inputs
14. Empty/loading/error states

### Phase 2 --- Core Pages

1.  Dashboard
2.  Analytics
3.  Model Health
4.  Logs
5.  Routing

### Phase 3 --- Developer Tools

1.  Sandbox
2.  API Keys
3.  Prompt Cache
4.  RAG
5.  Agent Pipelines
6.  Benchmarking Labs

### Phase 4 --- Polish

1.  Responsive behavior
2.  Accessibility
3.  Motion
4.  Theme consistency
5.  Edge states
6.  Loading/error states
7.  Visual consistency audit

------------------------------------------------------------------------

# 33. Definition of Done

Before finishing, verify every page in:

## Light mode

-   no unreadable text
-   no excessive white space
-   no broken borders
-   no poor contrast
-   no inconsistent card styles
-   no clipped tables

## Dark mode

-   no pure-white surfaces
-   no unreadable muted text
-   no excessive glow
-   no broken charts
-   no invisible borders
-   no contrast problems

## Functionality

Test:

-   navigation
-   theme switching
-   API key generation/revoke
-   cache controls
-   RAG ingest/search/delete
-   model health scan
-   refresh
-   logs filtering
-   benchmark execution
-   agent pipeline execution
-   routing
-   sandbox requests

------------------------------------------------------------------------

# 34. Critical Instruction to Antigravity

**Do not simply recolor the existing UI.**

Rework the visual hierarchy and component system.

The screenshots provided are references for the **current state**, not
the target.

The goal is a substantial UX/UI upgrade while preserving functionality.

Think:

``` text
Current:
Admin dashboard + cards + forms

Target:
AI Infrastructure Control Plane
+ Observability
+ Developer Console
+ Enterprise SaaS polish
```

Every screen should feel like part of the same product.

When you finish, perform a final pass across all routes and fix anything
visually inconsistent between pages.
