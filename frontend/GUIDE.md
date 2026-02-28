# 🚀 Developer Guide: Curtin Honestly Frontend

Welcome to the frontend of the Curtin Honestly review platform! This guide is designed to help you understand how the application is structured and how you can customize it.

## 📁 Project Structure

All the code you will work with is located in the `src/app` directory:

- **`models/`**: Defines the "shape" of our data (Interfaces). If the backend API changes, you update these files.
- **`services/`**: Handles all communication with the backend. Use the `UnitService` to fetch data.
- **`components/`**: The visual parts of our app.
    - `unit-list/`: The homepage grid of units.
    - `unit-detail/`: The dedicated page for unit descriptions and reviews.
- **`app.routes.ts`**: The "map" of the website. It links URLs (like `/units/COMP1000`) to specific components.
- **`app.html`**: The master layout (Header, Main Content, Footer).

---

## 🎨 Styling & Customization

We use a **Variable-First** styling approach to make it extremely easy to change the look of the site without digging through hundreds of lines of CSS.

### 1. Global Theme (`src/theme.css`)
This is your "Control Panel." To change the website's colors or fonts, simply edit the values in this file:
- Change `--primary-color` to update the signature Curtin yellow.
- Change `--main-font` or `--header-font` to switch the typography.
- **Custom Fonts**: We have imported custom fonts (Rubik, Roboto Condensed, Nimbus Sans) located in `public/assets/fonts/`. You can see how they are loaded using `@font-face` at the top of `theme.css`.

### 2. Component Styles
Each component has its own `.css` file for layout-specific styles:
- `unit-list.component.css`: Controls the grid and unit card appearance.
- `unit-detail.component.css`: Controls the layout of the review page and stat cards.

---

## 🛠 How to Edit the Frontend

### Adding a New Field to a Unit
1.  **Update the Model**: Add the field to `src/app/models/unit.model.ts`.
2.  **Update the Template**: Open the relevant `.html` file (e.g., `unit-list.component.html`) and add a new `<div>` or `<span>` to display `{{ unit.yourNewField }}`.

### Changing the Backend URL
If your backend moves from `localhost:8080`, update the `apiUrl` variable inside `src/app/services/unit.service.ts`.

### Creating a New Page
1.  Create a new folder in `components/` with `.ts`, `.html`, and `.css` files.
2.  Register the new component in `src/app/app.routes.ts`.

---

## ⚙️ Development Commands

Run these in the `frontend` directory:

- **`npm start`**: Runs the app in development mode. Open [http://localhost:4200](http://localhost:4200) to view it.
- **`npm run build`**: Compiles the app for production. Always run this before finishing a task to ensure there are no errors!

## 💡 Pro-Tip for Beginners
- **Pipes**: We use `| number:'1.1-1'` to round numbers and `| percent` to format ratios.
- **Directives**: `*ngFor` is used to loop through lists of units or reviews, and `*ngIf` is used to show or hide elements (like loading spinners).
- **Observables**: We use the `$` suffix (like `units$`) for variables that fetch data from the internet. We use the `async` pipe in HTML to handle them automatically.
