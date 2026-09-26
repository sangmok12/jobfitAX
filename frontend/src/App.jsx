import { useState } from 'react'
import './App.css'

const MAX_FILES = 10
const MAX_URLS = 10
const MAX_FILE_SIZE = 30 * 1024 * 1024
const ALLOWED_EXTENSIONS = ['pdf', 'docx', 'txt', 'md']

function App() {
  const [files, setFiles] = useState([])
  const [sourceUrls, setSourceUrls] = useState([])
  const [urlDraft, setUrlDraft] = useState('')
  const [requestState, setRequestState] = useState({ status: 'idle', message: '' })

  const selectFiles = (event) => {
    const selected = Array.from(event.target.files)
    const invalid = selected.find((file) => {
      const extension = file.name.split('.').pop()?.toLowerCase()
      return !ALLOWED_EXTENSIONS.includes(extension) || file.size > MAX_FILE_SIZE
    })

    if (invalid) {
      setRequestState({ status: 'error', message: 'PDF, DOCX, TXT, MD 형식의 30MB 이하 파일만 추가할 수 있습니다.' })
      event.target.value = ''
      return
    }

    setFiles((current) => {
      const unique = [...current, ...selected].filter((file, index, list) =>
        list.findIndex((item) => item.name === file.name && item.size === file.size) === index)
      if (unique.length > MAX_FILES) {
        setRequestState({ status: 'error', message: '파일은 최대 10개까지 추가할 수 있습니다.' })
        return current
      }
      setRequestState({ status: 'idle', message: '' })
      return unique
    })
    event.target.value = ''
  }

  const addSourceUrl = () => {
    const value = urlDraft.trim()
    try {
      const url = new URL(value)
      if (!['http:', 'https:'].includes(url.protocol)) throw new Error()
    } catch {
      setRequestState({ status: 'error', message: 'http 또는 https로 시작하는 올바른 URL을 입력해 주세요.' })
      return
    }

    if (sourceUrls.length >= MAX_URLS) {
      setRequestState({ status: 'error', message: 'URL은 최대 10개까지 추가할 수 있습니다.' })
      return
    }

    setSourceUrls((current) => current.includes(value) ? current : [...current, value])
    setUrlDraft('')
    setRequestState({ status: 'idle', message: '' })
  }

  const submit = async (event) => {
    event.preventDefault()
    setRequestState({ status: 'loading', message: '' })

    const values = new FormData(event.currentTarget)
    const formData = new FormData()
    files.forEach((file) => formData.append('files', file))
    sourceUrls.forEach((url) => formData.append('sourceUrls', url))
    formData.append('preferences', values.get('preferences'))
    formData.append('jobPostingUrl', values.get('jobPostingUrl'))

    try {
      const response = await fetch('/api/analyses', {
        method: 'POST',
        body: formData,
      })

      if (!response.ok) {
        throw new Error('요청을 처리하지 못했습니다.')
      }

      const result = await response.json()
      const fileCount = result.receivedFiles.length
      const urlCount = result.receivedUrls.length
      setRequestState({
        status: 'success',
        message: `분석 요청이 접수되었습니다. 파일 ${fileCount}개와 URL ${urlCount}개를 확인했습니다.`,
      })
    } catch {
      setRequestState({
        status: 'error',
        message: '백엔드 서버에 연결할 수 없습니다. 서버 실행 상태를 확인해 주세요.',
      })
    }
  }

  return (
    <div className="app-shell">
      <header>
        <a className="brand" href="/" aria-label="JobFit AX 홈">
          <span className="brand-mark">J</span> JobFit AX
        </a>
        <span className="badge">V1</span>
      </header>

      <main>
        <section className="intro">
          <span className="eyebrow">AI JOB MATCH ANALYSIS</span>
          <h1>내 경험과 채용공고의<br /><strong>진짜 연결점</strong>을 찾아보세요.</h1>
          <p>단순한 키워드 일치를 넘어, 실제 경험과 직무 내용을 AI가 의미적으로 비교합니다.</p>
        </section>

        <form onSubmit={submit}>
          <section className="form-card">
            <div className="section-title">
              <div><span>01</span><h2>내 자료 추가하기</h2></div>
              <em>모두 선택 사항</em>
            </div>
            <div className="source-guide">
              <p><strong>지원 파일</strong> PDF, DOCX, TXT, MD · 파일당 최대 30MB · 최대 10개</p>
              <p><strong>URL 안내</strong> 로그인이나 권한 제한 없이 누구나 볼 수 있는 전체 공개 페이지를 입력해 주세요.</p>
            </div>

            <div className="source-actions">
              <label className="file-button" htmlFor="sourceFiles">＋ 파일 추가</label>
              <input id="sourceFiles" className="hidden-file-input" type="file" multiple accept=".pdf,.docx,.txt,.md" onChange={selectFiles} />
              <div className="url-add">
                <input type="url" value={urlDraft} onChange={(event) => setUrlDraft(event.target.value)} placeholder="https://공개된-포트폴리오-주소" />
                <button type="button" onClick={addSourceUrl}>URL 추가</button>
              </div>
            </div>

            {(files.length > 0 || sourceUrls.length > 0) && (
              <div className="source-list">
                {files.map((file) => (
                  <div className="source-item" key={`${file.name}-${file.size}`}>
                    <span className="source-type">FILE</span><span>{file.name}</span>
                    <button type="button" onClick={() => setFiles((current) => current.filter((item) => item !== file))} aria-label={`${file.name} 삭제`}>×</button>
                  </div>
                ))}
                {sourceUrls.map((url) => (
                  <div className="source-item" key={url}>
                    <span className="source-type">URL</span><span>{url}</span>
                    <button type="button" onClick={() => setSourceUrls((current) => current.filter((item) => item !== url))} aria-label={`${url} 삭제`}>×</button>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="form-card">
            <div className="section-title">
              <div><span>02</span><h2>희망사항 알려주기</h2></div>
              <em>선택 사항</em>
            </div>
            <label className="field-label" htmlFor="preferences">추가 희망사항 또는 본인 의견</label>
            <textarea id="preferences" name="preferences" rows="5" placeholder="예: 서울 근무를 선호하고, AI를 실제 서비스에 적용하는 역할에 관심이 있습니다." />
          </section>

          <section className="form-card">
            <div className="section-title">
              <div><span>03</span><h2>채용공고 입력하기</h2></div>
              <em className="required">필수</em>
            </div>
            <label className="field-label" htmlFor="jobPostingUrl">채용공고 URL</label>
            <input id="jobPostingUrl" name="jobPostingUrl" type="url" placeholder="https://example.com/jobs/123" required />
          </section>

          <div className="submit-area">
            <button type="submit" disabled={requestState.status === 'loading'}>
              {requestState.status === 'loading' ? '요청 전송 중...' : 'JobFit 분석하기'}
              {requestState.status !== 'loading' && <span>→</span>}
            </button>
            <p>입력한 문서는 분석 요청에만 사용됩니다.</p>
            {requestState.message && (
              <div className={`notice ${requestState.status}`} role="status">
                {requestState.message}
              </div>
            )}
          </div>
        </form>
      </main>

      <footer><b>JobFit AX</b><span>나에게 맞는 일을 더 선명하게</span></footer>
    </div>
  )
}

export default App
