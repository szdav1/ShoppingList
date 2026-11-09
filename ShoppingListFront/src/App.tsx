import { lazy, Suspense, useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  IconButton,
  InputAdornment,
  Paper,
  TextField,
  Typography,
} from '@mui/material'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import CheckRounded from '@mui/icons-material/CheckRounded'
import LockOutlined from '@mui/icons-material/LockOutlined'
import PersonOutlineRounded from '@mui/icons-material/PersonOutlineRounded'
import ShoppingBasketOutlined from '@mui/icons-material/ShoppingBasketOutlined'
import Visibility from '@mui/icons-material/Visibility'
import VisibilityOff from '@mui/icons-material/VisibilityOff'
import {
  ApiError,
  clearSession,
  getCurrentUser,
  loadSession,
  login,
  logout,
  saveSession,
  type AuthSession,
} from './auth'
import './App.css'

type Screen = 'checking' | 'login' | 'authenticated'

const invalidCredentialsMessage = 'Érvénytelen felhasználónév vagy jelszó!'
const Workspace = lazy(() => import('./Workspace'))

function App() {
  const [screen, setScreen] = useState<Screen>('checking')
  const [session, setSession] = useState<AuthSession | null>(null)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [message, setMessage] = useState('')

  useEffect(() => {
    const savedSession = loadSession()
    if (!savedSession) {
      setScreen('login')
      return
    }

    const controller = new AbortController()
    void getCurrentUser(savedSession.accessToken, controller.signal)
      .then((currentUser) => {
        const restoredSession = { ...savedSession, user: currentUser }
        saveSession(restoredSession)
        setSession(restoredSession)
        setScreen('authenticated')
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return

        if (error instanceof ApiError && error.status === 401) {
          clearSession()
          setSession(null)
        } else {
          setMessage('Nem sikerült ellenőrizni a munkamenetet. Ellenőrizd a kapcsolatot, majd jelentkezz be újra.')
        }
        setScreen('login')
      })

    return () => controller.abort()
  }, [])

  const handleLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setMessage('')
    setIsSubmitting(true)

    try {
      const session = await login(username.trim(), password)
      saveSession(session)
      setSession(session)
      setScreen('authenticated')
      setPassword('')
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setMessage(invalidCredentialsMessage)
      } else {
        setMessage(error instanceof Error ? error.message : 'A bejelentkezés nem sikerült.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleLogout = useCallback(async () => {
    const savedSession = session ?? loadSession()
    if (!savedSession) {
      setSession(null)
      setScreen('login')
      return
    }

    setMessage('')
    setIsSubmitting(true)
    try {
      await logout(savedSession.accessToken, savedSession.tokenType)
      clearSession()
      setSession(null)
      setUsername('')
      setScreen('login')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'A kijelentkezés nem sikerült.')
    } finally {
      setIsSubmitting(false)
    }
  }, [session])

  const handleSessionExpired = useCallback(() => {
    clearSession()
    setSession(null)
    setScreen('login')
    setMessage('A munkamenet lejárt. Jelentkezz be újra.')
  }, [])

  if (screen === 'authenticated' && session) {
    return (
      <Suspense fallback={<Box className="checking-state" role="status"><CircularProgress size={28} /></Box>}>
        <Workspace
          session={session}
          logoutError={message}
          onLogout={handleLogout}
          onSessionExpired={handleSessionExpired}
        />
      </Suspense>
    )
  }

  return (
    <Box component="main" className="auth-page">
      <Box component="section" className="story-panel" aria-label="Kosár">
        <Box className="brand-lockup">
          <ShoppingBasketOutlined aria-hidden="true" />
          <span>kosár</span>
        </Box>

        <Box className="story-copy">
          <Typography className="story-eyebrow">BEVÁSÁRLÓLISTA, EGYÜTT</Typography>
          <Typography component="h1" className="story-title">
            Kevesebb fejben tartani. Több együtt megoldani.
          </Typography>
          <Typography className="story-description">
            A közös bevásárlás itt kezdődik.
          </Typography>
        </Box>

        <Paper className="list-preview" elevation={0} aria-hidden="true">
          <Box className="preview-heading">
            <Box>
              <span className="preview-kicker">A MAI TERV</span>
              <Typography component="h2">Heti bevásárlás</Typography>
            </Box>
            <span className="preview-count">03</span>
          </Box>
          <Box className="preview-row preview-row-done">
            <span className="preview-check"><CheckRounded /></span>
            <span>Szezonális alma</span>
          </Box>
          <Box className="preview-row">
            <span className="preview-check" />
            <span>Kovászos kenyér</span>
          </Box>
          <Box className="preview-row">
            <span className="preview-check" />
            <span>Friss zöldfűszerek</span>
          </Box>
          <Box className="preview-footer">
            <span className="preview-dot" />
            Közös lista
          </Box>
        </Paper>

        <Typography className="story-footer">Jó dolgok, egy helyen.</Typography>
      </Box>

      <Box component="section" className="form-panel">
        <Box className="form-inner">
          <Box className="mobile-brand brand-lockup">
            <ShoppingBasketOutlined aria-hidden="true" />
            <span>kosár</span>
          </Box>

          {screen === 'checking' ? (
            <Box className="checking-state" role="status">
              <CircularProgress size={28} />
              <Typography>Munkamenet ellenőrzése...</Typography>
            </Box>
          ) : (
            <>
              <Box className="form-heading">
                <Typography className="form-eyebrow">ÜDV ÚJRA</Typography>
                <Typography component="h2" className="form-title">
                  Bejelentkezés
                </Typography>
                <Typography className="form-description">
                  Add meg az adataidat a folytatáshoz.
                </Typography>
              </Box>

              {message && (
                <Alert className="form-alert" severity="error" role="alert">
                  {message}
                </Alert>
              )}

              <Box component="form" className="login-form" onSubmit={handleLogin}>
                <TextField
                  label="Felhasználónév"
                  name="username"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  autoComplete="username"
                  autoCapitalize="none"
                  required
                  fullWidth
                  slotProps={{
                    htmlInput: { maxLength: 30 },
                    input: {
                      startAdornment: (
                        <InputAdornment position="start">
                          <PersonOutlineRounded fontSize="small" />
                        </InputAdornment>
                      ),
                    },
                  }}
                />
                <TextField
                  label="Jelszó"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete="current-password"
                  required
                  fullWidth
                  slotProps={{
                    htmlInput: { minLength: 8 },
                    input: {
                      startAdornment: (
                        <InputAdornment position="start">
                          <LockOutlined fontSize="small" />
                        </InputAdornment>
                      ),
                      endAdornment: (
                        <InputAdornment position="end">
                          <IconButton
                            aria-label={showPassword ? 'Jelszó elrejtése' : 'Jelszó megjelenítése'}
                            onClick={() => setShowPassword((visible) => !visible)}
                            edge="end"
                          >
                            {showPassword ? <VisibilityOff /> : <Visibility />}
                          </IconButton>
                        </InputAdornment>
                      ),
                    },
                  }}
                />
                <Button
                  type="submit"
                  variant="contained"
                  disabled={isSubmitting}
                  endIcon={isSubmitting ? <CircularProgress size={17} color="inherit" /> : <ArrowForwardRounded />}
                  fullWidth
                >
                  {isSubmitting ? 'Bejelentkezés...' : 'Belépés'}
                </Button>
              </Box>

              <Typography className="form-note">
                A folytatással biztonságos munkamenetet indítasz.
              </Typography>
            </>
          )}
        </Box>
      </Box>
    </Box>
  )
}

export default App
