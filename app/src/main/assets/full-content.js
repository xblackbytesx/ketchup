async function displayFullContent(url, html) {
  try {
    const result = await Mercury.parse(url, { html: html });
    const body = document.getElementById('article-body');
    if (body && result && result.content) {
      body.innerHTML = result.content;
    }
  } catch (e) {
    console.error('Mercury failed:', e);
    // Original body (raw HTML) remains intact
  }
}
