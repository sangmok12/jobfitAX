import { useEffect, useState } from 'react'
import './App.css'

const MAX_FILES = 10
const MAX_URLS = 10
const MAX_FILE_SIZE = 30 * 1024 * 1024
const ALLOWED_EXTENSIONS = ['pdf', 'docx', 'txt', 'md']

function OpeningScene({ leaving }) {
  return (
    <div className={`opening-scene${leaving ? ' is-leaving' : ''}`} aria-label="JobFit AX 시작 화면">
      <div className="opening-backdrop" aria-hidden="true">
        <span className="opening-beam beam-left" />
        <span className="opening-beam beam-right" />
        <span className="opening-orbit" />
      </div>
      <div className="opening-content">
        <div className="opening-logo-wrap">
          <span className="opening-logo-glow" />
          <img src="/jobfit-logo.svg" alt="" />
        </div>
        <div className="opening-wordmark">JobFit <strong>AX</strong></div>
        <p>나의 경험과 더 좋은 기회가 만나는 순간</p>
        <div className="opening-progress" aria-hidden="true"><span /></div>
      </div>
    </div>
  )
}

function LoadingScene() {
  return (
    <div className="loading-scene" role="status" aria-live="polite">
      <div className="loading-card">
        <div className="loading-brand" aria-label="JobFit AX">JobFit <strong>AX</strong></div>
        <span className="loading-light" aria-hidden="true" />
        <p>
          당신이 빛날 순간을 발견하고 있어요
          <span className="loading-dots" aria-hidden="true"><i>.</i><i>.</i><i>.</i></span>
        </p>
      </div>
    </div>
  )
}

function UrlExtractionCard({ source, siteName, officiallySupported, ocr }) {
  return (
    <article className="extraction-card">
      <div className="extraction-summary">
        <div>
          <span className={`status-dot ${source.status.toLowerCase()}`} />
          <strong>{source.pageTitle || source.sourceUrl}</strong>
        </div>
        <span className="status-label">
          {source.status === 'SUCCESS'
            ? '본문 추출 완료'
            : source.status === 'BLOCKED'
              ? '접근 차단'
              : source.status === 'AUTH_REQUIRED'
                ? '공개 설정 필요'
                : '추출 실패'}
        </span>
      </div>
      {siteName && (
        <div className="site-labels">
          <span>{siteName}</span>
          <span className={officiallySupported ? 'supported' : ''}>
            {officiallySupported ? '지원 사이트' : '범용 분석'}
          </span>
        </div>
      )}
      {ocr && (
        <div className={`ocr-notice ${ocr.status.toLowerCase()}`}>
          <strong>
            {ocr.status === 'SUCCESS'
              ? `상세 이미지 OCR ${ocr.includedImageCount}개 · ${ocr.characterCount.toLocaleString()}자`
              : ocr.status === 'FRAME_TEXT'
                ? `상세공고 HTML · ${ocr.characterCount.toLocaleString()}자`
              : ocr.status === 'LOW_QUALITY' ? '상세 이미지 OCR 확인 필요' : '상세 이미지 없음'}
          </strong>
          <span>{ocr.message}</span>
        </div>
      )}
      <a className="source-url" href={source.finalUrl} target="_blank" rel="noreferrer">{source.finalUrl}</a>

      {source.status === 'SUCCESS' ? (
        <>
          <div className="extraction-stats">
            <div><span>페이지 전체</span><strong>{source.rawCharacterCount.toLocaleString()}자</strong></div>
            <div><span>본문 추출 후</span><strong>{source.cleanedCharacterCount.toLocaleString()}자</strong></div>
            <div><span>AI 전달 대상</span><strong>{source.analysisCharacterCount.toLocaleString()}자</strong></div>
            <div><span>비본문 제외</span><strong>{source.excludedCharacterCount.toLocaleString()}자</strong></div>
            <div><span>길이 제한 제외</span><strong>{source.truncatedCharacterCount.toLocaleString()}자</strong></div>
          </div>
          <div className="rule-list">
            {source.removalStats.length > 0
              ? source.removalStats.map((stat) => (
                <span key={stat.category}>✓ {stat.category} {stat.characterCount.toLocaleString()}자</span>
              ))
              : <span>명확한 비본문 영역 없음</span>}
          </div>
          <details>
            <summary>정제 내용 상세보기</summary>
            <div className="text-comparison">
              <div>
                <h3>페이지 전체 표시 텍스트</h3>
                <pre>{source.rawText}</pre>
              </div>
              <div>
                <h3>핵심 본문 · AI 전달 내용</h3>
                <pre>{source.normalizedText}</pre>
              </div>
            </div>
          </details>
        </>
      ) : (
        <p className="extraction-error">{source.errorMessage}</p>
      )}
    </article>
  )
}

function App() {
  const [openingState, setOpeningState] = useState('showing')
  const [files, setFiles] = useState([])
  const [sourceUrls, setSourceUrls] = useState([])
  const [urlDraft, setUrlDraft] = useState('')
  const [extractedSources, setExtractedSources] = useState([])
  const [extractedUrls, setExtractedUrls] = useState([])
  const [jobPosting, setJobPosting] = useState(null)
  const [preferences, setPreferences] = useState('')
  const [requestState, setRequestState] = useState({ status: 'idle', message: '' })
  const hasUserMaterials = files.length > 0 || sourceUrls.length > 0 || preferences.trim().length > 0

  useEffect(() => {
    document.body.classList.add('opening-active')
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const leaveTimer = window.setTimeout(() => setOpeningState('leaving'), reduceMotion ? 350 : 2300)
    const finishTimer = window.setTimeout(() => {
      setOpeningState('hidden')
      document.body.classList.remove('opening-active')
    }, reduceMotion ? 550 : 3000)

    return () => {
      window.clearTimeout(leaveTimer)
      window.clearTimeout(finishTimer)
      document.body.classList.remove('opening-active')
    }
  }, [])

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
    setExtractedSources([])
    setExtractedUrls([])
    setJobPosting(null)

    const values = new FormData(event.currentTarget)
    const formData = new FormData()
    files.forEach((file) => formData.append('files', file))
    sourceUrls.forEach((url) => formData.append('sourceUrls', url))
    formData.append('preferences', preferences)
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
      setExtractedSources(result.extractedSources)
      setExtractedUrls(result.extractedUrls)
      setJobPosting(result.jobPosting)
      setRequestState({
        status: 'success',
        message: hasUserMaterials
          ? `채용공고와 사용자 자료 정리가 완료되었습니다. 파일 ${fileCount}개와 URL ${urlCount}개를 확인했습니다.`
          : '채용공고 정리가 완료되었습니다.',
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
      {openingState !== 'hidden' && <OpeningScene leaving={openingState === 'leaving'} />}
      {requestState.status === 'loading' && <LoadingScene />}
      <header>
        <a className="brand" href="/" aria-label="JobFit AX 홈">
          <img className="brand-logo" src="/jobfit-logo.svg" alt="" /> JobFit AX
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
            <textarea id="preferences" name="preferences" rows="5" value={preferences} onChange={(event) => setPreferences(event.target.value)} placeholder="예: 서울 근무를 선호하고, AI를 실제 서비스에 적용하는 역할에 관심이 있습니다." />
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
              {requestState.status === 'loading'
                ? '분석 중...'
                : hasUserMaterials ? 'JobFit 분석하기' : '채용공고 분석하기'}
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

        {jobPosting?.extraction && (
          <section className="extraction-results" aria-labelledby="job-posting-title">
            <div className="results-heading">
              <div>
                <span className="eyebrow">JOB POSTING INSPECTION</span>
                <h2 id="job-posting-title">AI에 전달할 채용공고</h2>
              </div>
              <span>{jobPosting.siteName}</span>
            </div>
            <p className="results-description">
              실제 화면에 렌더링된 채용공고에서 업무, 자격요건 등 핵심 본문을 추출했습니다.
            </p>
            <div className="extraction-list">
              <UrlExtractionCard
                source={jobPosting.extraction}
                siteName={jobPosting.siteName}
                officiallySupported={jobPosting.officiallySupported}
                ocr={jobPosting.ocr}
              />
            </div>
          </section>
        )}

        {(extractedSources.length > 0 || extractedUrls.length > 0) && (
          <section className="extraction-results" aria-labelledby="extraction-title">
            <div className="results-heading">
              <div>
                <span className="eyebrow">SOURCE INSPECTION</span>
                <h2 id="extraction-title">AI에 전달할 사용자 자료</h2>
              </div>
              <span>{extractedSources.length + extractedUrls.length}개 자료</span>
            </div>
            <p className="results-description">
              지금은 AI를 호출하지 않았습니다. 아래 정제 결과가 이후 AI 분석에 사용될 내용입니다.
            </p>

            <div className="extraction-list">
              {extractedSources.map((source) => (
                <article className="extraction-card" key={source.sourceName}>
                  <div className="extraction-summary">
                    <div>
                      <span className={`status-dot ${source.status.toLowerCase()}`} />
                      <strong>{source.sourceName}</strong>
                    </div>
                    <span className="status-label">
                      {source.status === 'SUCCESS' ? '추출 완료' : source.status === 'NO_TEXT' ? '텍스트 없음' : '추출 실패'}
                    </span>
                  </div>

                  {source.status === 'SUCCESS' ? (
                    <>
                      <div className="extraction-stats">
                        <div><span>원문</span><strong>{source.rawCharacterCount.toLocaleString()}자</strong></div>
                        <div><span>기본 정제 후</span><strong>{source.cleanedCharacterCount.toLocaleString()}자</strong></div>
                        <div><span>AI 전달 대상</span><strong>{source.analysisCharacterCount.toLocaleString()}자</strong></div>
                        <div><span>기본 정제 제외</span><strong>{source.removedCharacterCount.toLocaleString()}자</strong></div>
                        <div><span>길이 제한 제외</span><strong>{source.truncatedCharacterCount.toLocaleString()}자</strong></div>
                      </div>
                      <div className="rule-list">
                        {source.appliedRules.length > 0
                          ? source.appliedRules.map((rule) => <span key={rule}>✓ {rule}</span>)
                          : <span>별도 정제 없음</span>}
                      </div>
                      <details>
                        <summary>정제 내용 상세보기</summary>
                        <div className="text-comparison">
                          <div>
                            <h3>추출 원문</h3>
                            <pre>{source.rawText}</pre>
                          </div>
                          <div>
                            <h3>정제 후 · AI 전달 내용</h3>
                            <pre>{source.normalizedText}</pre>
                          </div>
                        </div>
                      </details>
                    </>
                  ) : (
                    <p className="extraction-error">{source.errorMessage}</p>
                  )}
                </article>
              ))}
              {extractedUrls.map((source) => (
                <UrlExtractionCard source={source} key={source.sourceUrl} />
              ))}
            </div>
          </section>
        )}
      </main>

      <footer><b>JobFit AX</b><span>나에게 맞는 일을 더 선명하게</span></footer>
    </div>
  )
}

export default App
