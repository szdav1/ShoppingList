import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { CssBaseline, ThemeProvider, createTheme } from '@mui/material'
import './index.css'
import App from './App.tsx'

const theme = createTheme({
  palette: {
    primary: { main: '#1d503f', contrastText: '#fffdf4' },
    secondary: { main: '#c87858' },
    background: { default: '#f7f6f0', paper: '#fffefa' },
    text: { primary: '#24352b', secondary: '#70786f' },
  },
  shape: { borderRadius: 6 },
  typography: {
    fontFamily: "'Trebuchet MS', 'Segoe UI', sans-serif",
    button: { fontWeight: 700 },
  },
  components: {
    MuiButton: { styleOverrides: { root: { textTransform: 'none' } } },
    MuiOutlinedInput: { styleOverrides: { root: { borderRadius: 7 } } },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <App />
    </ThemeProvider>
  </StrictMode>,
)
