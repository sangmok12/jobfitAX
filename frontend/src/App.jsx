import { useState } from 'react'
import './App.css'

const documents = [
  ['resume', '이력서', '현재 경력과 기술을 확인할 수 있는 문서'],
  ['career', '경력기술서', '프로젝트와 담당 업무를 자세히 적은 문서'],
  ['portfolio', '포트폴리오', '작업 결과와 경험을 보여주는 문서'],
]

function App() {
  const [files, setFiles] = useState({})
  const [notice, setNotice] = useState(false)

  const selectFile = (event, id) => {
    setFiles((current) => ({ ...current, [id]: event.target.files[0]?.name ?? '' }))
  }

  const submit = (event) => {
    event.preventDefault()
    setNotice(true)
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
              <div><span>01</span><h2>내 문서 올리기</h2></div>
              <em>모두 선택 사항</em>
            </div>
            <p className="help">가지고 있는 문서만 올려주세요. PDF, DOC, DOCX 파일을 지원합니다.</p>
            <div className="upload-grid">
              {documents.map(([id, label, description]) => (
                <label className="upload-card" htmlFor={id} key={id}>
                  <span className="upload-icon">↑</span>
                  <strong>{label}</strong>
                  <small>{files[id] || description}</small>
                  <b>{files[id] ? '변경' : '파일 선택'}</b>
                  <input id={id} type="file" accept=".pdf,.doc,.docx" onChange={(e) => selectFile(e, id)} />
                </label>
              ))}
            </div>
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
            <button type="submit">JobFit 분석하기 <span>→</span></button>
            <p>입력한 문서는 분석 요청에만 사용됩니다.</p>
            {notice && <div className="notice" role="status">입력 화면이 준비되었습니다. 다음 단계에서 AI 분석 기능을 연결합니다.</div>}
          </div>
        </form>
      </main>

      <footer><b>JobFit AX</b><span>나에게 맞는 일을 더 선명하게</span></footer>
    </div>
  )
}

export default App
