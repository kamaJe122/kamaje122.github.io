
document.getElementById('saveSnippet').addEventListener('click', () => {
  const snippet = document.getElementById('newSnippet').value;
  if (snippet) {
    chrome.storage.sync.get({ snippets: [] }, (data) => {
      const snippets = data.snippets;
      snippets.push(snippet);
      chrome.storage.sync.set({ snippets }, () => {
        displaySnippets();
        document.getElementById('newSnippet').value = '';
      });
    });
  }
});

function displaySnippets() {
  chrome.storage.sync.get({ snippets: [] }, (data) => {
    const snippetsList = document.getElementById('snippetsList');
    snippetsList.innerHTML = '';
    data.snippets.forEach((snippet, index) => {
      const li = document.createElement('li');
      
      // 新增編輯功能
      const snippetText = document.createElement('span');
      snippetText.textContent = snippet;
      snippetText.addEventListener('dblclick', () => {
        const input = document.createElement('input');
        input.value = snippet;
        li.replaceChild(input, snippetText);
        input.focus();
        input.addEventListener('blur', () => {
          data.snippets[index] = input.value;
          chrome.storage.sync.set({ snippets: data.snippets }, displaySnippets);
        });
      });
      li.appendChild(snippetText);

      // 複製按鈕
      const copyButton = document.createElement('button');
      copyButton.textContent = '複製';
      copyButton.addEventListener('click', () => {
        navigator.clipboard.writeText(snippet).then(() => {
          console.log('文字已複製到剪貼板');
        }).catch(err => {
          console.error('無法複製文字: ', err);
        });
      });

      // 刪除按鈕
      const deleteButton = document.createElement('button');
      deleteButton.textContent = '刪除';
      deleteButton.addEventListener('click', () => {
        data.snippets.splice(index, 1);
        chrome.storage.sync.set({ snippets: data.snippets }, displaySnippets);
      });

      // 拖動功能
      li.draggable = true;
      li.addEventListener('dragstart', (e) => {
        e.dataTransfer.setData('text/plain', index);
      });

      li.addEventListener('dragover', (e) => {
        e.preventDefault();
      });

      li.addEventListener('drop', (e) => {
        e.preventDefault();
        const draggedIndex = e.dataTransfer.getData('text/plain');
        const targetIndex = index;
        const draggedSnippet = data.snippets[draggedIndex];
        data.snippets.splice(draggedIndex, 1);
        data.snippets.splice(targetIndex, 0, draggedSnippet);
        chrome.storage.sync.set({ snippets: data.snippets }, displaySnippets);
      });

      li.appendChild(copyButton);
      li.appendChild(deleteButton);
      snippetsList.appendChild(li);
    });
  });
}

document.addEventListener('DOMContentLoaded', displaySnippets);