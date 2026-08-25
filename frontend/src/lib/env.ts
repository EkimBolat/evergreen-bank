// Empty by default: same-origin (via nginx/Vite proxy) for local dev and the Docker stack.
// Set VITE_API_BASE_URL to the backend's full origin when the frontend is deployed separately
// from the backend (e.g. two independent Render services).
export const API_ORIGIN = import.meta.env.VITE_API_BASE_URL ?? ''
