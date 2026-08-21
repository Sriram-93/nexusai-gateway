# NexusAI Gateway

Build a premium, end-to-end B2B Enterprise AI Gateway application called "NexusAI". The application should have a "Quiet Luxury" aesthetic: an immersive, deep dark mode (background #06080A), extreme glassmorphism, 1-pixel subtle borders, and glowing mesh gradients (cyan, emerald, and amber) in the background. Use 'Inter' font. The UI must feel incredibly advanced, highly polished, and lovable, with smooth micro-animations on every interaction.

Please build the following complete flow and pages:

1. Landing / Auth Page (Login & Signup)
- A stunning split-screen auth page. The left side features a mesmerizing, slowly rotating 3D glowing particle mesh or a deep animated gradient that represents AI neural routing, with the text "NexusAI: The Ultimate Adaptive Routing Gateway".
- The right side is a glassmorphic login/signup form. It should have tabs to switch between "Sign In" and "Create Account".
- Inputs for Organization Name, Email, and Password.
- The buttons should have a premium gradient background (Indigo to Cyan) with a subtle glow effect on hover.

2. Onboarding / Provisioning Page (Post-Signup)
- After signing up, show a beautiful success modal.
- It must display a newly generated API Key (e.g., `nx_live_...`) inside a secure, dashed-border box.
- Include a "Copy to Clipboard" button with a Lucide checkmark icon animation when clicked.
- Add a warning note: "Please save this secret key. It will not be shown again."
- Below it, a "Bring Your Own Key (BYOK)" section with password inputs to securely save their upstream OpenAI and Groq API keys.

3. Main Application Shell (The Dashboard)
- A permanent left sidebar navigation that is glassmorphic and blurry. It should have categories: Workspace (Dashboard, Sandbox), Infrastructure (Routing, Providers, API Keys), and Observability (Analytics, Logs).
- The sidebar active state should have a glowing left border and bright cyan text.
- A top navbar showing the current page title and a "System Operational" status pill with a pulsing green dot.

4. "My App" / Dashboard View
- A "Bento Box" style grid of metric cards: Active Agents, Total Requests, Avg Latency (e.g., 24ms), and Total Token Cost.
- The metric cards should have a deep dark background, frosted glass blur, and a subtle white border that highlights on hover with a smooth upward translation (Framer Motion).
- A large central panel showing a live "Activity Stream" table of API requests, displaying Timestamp, Provider, Model, Latency, and Status (Success/Fail badges).

5. Routing Engine & Settings View
- A screen to configure the AI routing logic.
- Show sleek, selectable toggle cards for routing strategies: "Static", "Rule-Based", "Weighted", and "Federated LinUCB".
- If "Weighted" is selected, dynamically reveal slider inputs to adjust traffic percentages between Gemini 2.5 and Groq Llama 3.

Technical Requirements:
- Use Tailwind CSS for all styling. Use rich custom colors (e.g., `bg-[#06080A]`).
- Heavily utilize `backdrop-blur-md` and `bg-white/5` for the glassmorphic panels.
- Use Lucide React for all icons.
- Use Shadcn UI components for buttons, inputs, tables, and tabs, heavily customizing them to fit the dark luxury theme.
- Add Framer Motion for page transitions, tab switching, and hover effects on cards.
- Make it fully responsive, but optimize for desktop enterprise users.

This project was built with [Lovable](https://lovable.dev).

## Build with Lovable

Continue developing this project in the [Lovable editor](https://lovable.dev/projects/fcbccfe4-9fcc-41a0-bee4-17dfae2601de).

- **Ship faster**: describe what you want to build and Lovable handles the code.
- **Stay in sync**: every change made in Lovable is committed straight to this repository.
- **Full ownership**: this code is yours. Push to `main` on GitHub and your changes sync back into Lovable, ready for your next prompt.

## Development

Prefer working locally? You need Node.js and npm — [install with nvm](https://github.com/nvm-sh/nvm#installing-and-updating).

```sh
git clone <this-repository-url>
cd <repository-name>
npm i
npm run dev
```
