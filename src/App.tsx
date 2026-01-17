import { useState } from "react";
import { StickyNote, Calendar, Contact } from "lucide-react";

// 分頁定義（含icon與文字）
const TABS = [
  {
    key: "paste",
    label: "便條紙",
    icon: <StickyNote size={22} />,
  },
  {
    key: "calendar",
    label: "日曆",
    icon: <Calendar size={22} />,
  },
  {
    key: "card",
    label: "名片",
    icon: <Contact size={22} />,
  },
];

function App() {
  const [tab, setTab] = useState("paste");

  // ------------------- 便條紙 Paste 功能 ------------------
  const [pasteValue, setPasteValue] = useState("");
  const [pasteCopies, setPasteCopies] = useState<string[]>([]);

  function handlePasteAdd() {
    if (pasteValue.trim() !== "") {
      setPasteCopies([pasteValue, ...pasteCopies]);
      setPasteValue("");
    }
  }
  // ------------------------------------------------------

  // ------------------- Lazy Calendar 功能 ----------------
  const [calendarTodos, setCalendarTodos] = useState<{date: string; todo: string}[]>([]);
  const [calendarInput, setCalendarInput] = useState("");
  const [calendarDate, setCalendarDate] = useState<string>(new Date().toISOString().slice(0,10));
  function handleAddCalendar() {
    if (calendarInput.trim() !== "") {
      setCalendarTodos([{date: calendarDate, todo: calendarInput}, ...calendarTodos]);
      setCalendarInput("");
    }
  }
  // ------------------------------------------------------

  // ------------------- Business Card 功能 ----------------
  const [cardPerson, setCardPerson] = useState({
    name: "王大明",
    job: "前端工程師",
    email: "damian@example.com",
    phone: "0912345678",
  });
  const [cardEdit, setCardEdit] = useState(false);
  // ------------------------------------------------------

  function renderTab() {
    switch (tab) {
      case "paste":
        // Paste 便條紙實作區
        return (
          <section className="px-4 pb-20 pt-6"> {/* 便條紙區塊 */}
            {/* 便條紙表單 */}
            <div className="mb-4">
              <input
                className="w-full py-2 px-3 rounded bg-white text-black placeholder-[#ffc400] border border-yellow-300 focus:outline-none mb-3"
                type="text"
                placeholder="輸入你的便條內容..."
                value={pasteValue}
                onChange={e => setPasteValue(e.target.value)}
                onKeyDown={e => e.key==="Enter" && handlePasteAdd()}
              />
              <button
                className="w-full bg-[#FFC400] text-black py-2 rounded font-bold"
                onClick={handlePasteAdd}
              >新增便條</button>
            </div>
            {/* 便條紙清單 */}
            <ul className="space-y-2">
              {pasteCopies.length===0 && <li className="text-yellow-900/40 text-center">尚無便條</li>}
              {pasteCopies.map((txt,idx)=>(
                <li key={idx} className="bg-white text-yellow-900 rounded p-3 shadow-sm border border-yellow-100">{txt}</li>
              ))}
            </ul>
          </section>
        );
      case "calendar":
        // Lazy Calendar 實作區
        return (
          <section className="px-4 pb-20 pt-6"> {/* 日曆區塊 */}
            <div className="flex gap-2 mb-3 items-center">
              <input
                type="date"
                className="rounded border-yellow-200 px-2 py-1 text-black"
                value={calendarDate}
                onChange={e => setCalendarDate(e.target.value)}
              />
              <input
                className="flex-grow py-2 px-3 rounded bg-white text-black placeholder-[#ffc400] border border-yellow-300 focus:outline-none"
                type="text"
                placeholder="新增待辦事項..."
                value={calendarInput}
                onChange={e => setCalendarInput(e.target.value)}
                onKeyDown={e=>e.key==="Enter" && handleAddCalendar()}
              />
              <button
                className="bg-[#FFC400] text-black py-1 px-3 rounded font-bold"
                onClick={handleAddCalendar}
              >+</button>
            </div>
            {/* 日曆ToDo清單 */}
            <ul className="space-y-2">
              {calendarTodos.length===0 && <li className="text-yellow-900/40 text-center">尚無待辦</li>}
              {calendarTodos.filter(t=>t.date===calendarDate).map((t,idx)=>(
                <li key={idx} className="bg-white rounded text-yellow-900 p-2 shadow border border-yellow-100 ">{t.todo}</li>
              ))}
            </ul>
          </section>
        );
      case "card":
        // Business Card 實作區
        return (
          <section className="px-4 pb-20 pt-6 flex flex-col items-center">
            <div className="rounded-xl shadow-lg bg-[#FFC400] px-6 py-6 text-black w-full max-w-xs ">
              {cardEdit ? (
                <div className="space-y-2">
                  <input className="w-full rounded px-3 py-1 mb-1 border border-yellow-400 text-black" value={cardPerson.name} onChange={e=>setCardPerson(v=>({...v,name:e.target.value}))} />
                  <input className="w-full rounded px-3 py-1 mb-1 border border-yellow-400 text-black" value={cardPerson.job} onChange={e=>setCardPerson(v=>({...v,job:e.target.value}))} />
                  <input className="w-full rounded px-3 py-1 mb-1 border border-yellow-400 text-black" value={cardPerson.email} onChange={e=>setCardPerson(v=>({...v,email:e.target.value}))} />
                  <input className="w-full rounded px-3 py-1 mb-2 border border-yellow-400 text-black" value={cardPerson.phone} onChange={e=>setCardPerson(v=>({...v,phone:e.target.value}))} />
                  <button className="bg-yellow-800 text-white py-1 px-4 rounded w-full font-bold" onClick={()=>setCardEdit(false)}>儲存</button>
                </div>
              ) : (
                <div>
                  <div className="text-2xl font-bold mb-1">{cardPerson.name}</div>
                  <div className="text-md mb-1">{cardPerson.job}</div>
                  <div className="mb-1">✉️ {cardPerson.email}</div>
                  <div className="">📞 {cardPerson.phone}</div>
                  <button className="mt-4 bg-yellow-800 text-white py-1 px-4 rounded w-full font-bold" onClick={()=>setCardEdit(true)}>編輯</button>
                </div>
              )}
            </div>
          </section>
        );
      default:
        return null;
    }
  }

  return (
    <div className="flex flex-col min-h-screen bg-[#FFC400]/10">
      <main className="flex-grow">{renderTab()}</main>
      {/* 手機底部 Tab Bar */}
      <nav className="fixed inset-x-0 bottom-0 h-16 bg-[#FFC400] border-t border-yellow-300 flex justify-around items-center z-10 shadow-lg">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`flex flex-col items-center justify-center transition-all ${tab === t.key ? "text-black font-semibold" : "text-yellow-800/70 font-normal"}`}
          >
            {t.icon}
            <span className="text-xs mt-1">{t.label}</span>
          </button>
        ))}
      </nav>
    </div>
  );
}

export default App;
