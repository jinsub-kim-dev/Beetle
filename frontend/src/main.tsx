import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from './App'
import './index.css'

const container = document.getElementById('root')
if (!container) {
  throw new Error('#root 요소를 찾을 수 없습니다. index.html 을 확인하세요.')
}

createRoot(container).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
