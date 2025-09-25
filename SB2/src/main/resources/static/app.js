document.addEventListener('DOMContentLoaded', () => {
    const $ = id => document.getElementById(id);
    const show = (id, data) => { const el = $(id); if (el) el.textContent = typeof data === 'string' ? data : JSON.stringify(data, null, 2); };
    const api = (url, method='GET', body) =>
        fetch(url, { method, headers:{'Content-Type':'application/json'}, body: body?JSON.stringify(body):undefined })
            .then(async r => ({ ok:r.ok, status:r.status, data: await r.json().catch(()=>({})) }));

    // Motivational quote
    if (document.body.id === 'home-page') {
        const showQuote = async () => {
            const response = await fetch('/motivational');
            if (response.ok) {
                const html = await response.text();
                // парсимо текст цитати з HTML (або можна зробити окремий JSON-ендпоінт)
                const temp = document.createElement('div');
                temp.innerHTML = html;
                const quote = temp.querySelector('.quote')?.textContent || "Цитата недоступна";
                document.getElementById('motivational-text').textContent = quote;
            } else {
                document.getElementById('motivational-text').textContent = "Цитата недоступна";
            }
        };

        showQuote();
        document.getElementById('refresh-quote')?.addEventListener('click', showQuote);
    }

    // Workouts
    if (document.body.id === 'workouts-page') {
        $('w-list')?.addEventListener('click', async () => { const r = await api('/api/workouts'); show('w-out-list', r.data); });
        $('w-one-btn')?.addEventListener('click', async () => { const id = $('w-one-id').value.trim(); if(!id) return; const r = await api(`/api/workouts/${id}`); show('w-out-one', r.data); });
        $('w-create')?.addEventListener('click', async () => {
            const body = { date:$('w-c-date').value, type:$('w-c-type').value, minutes:Number($('w-c-min').value||0), reps:Number($('w-c-reps').value||0), comment:$('w-c-comment').value };
            const r = await api('/api/workouts','POST', body); show('w-out-create', r.data);
        });
        $('w-update')?.addEventListener('click', async () => {
            const id = $('w-u-id').value.trim(); if(!id) return;
            const body = { date:$('w-u-date').value, type:$('w-u-type').value, minutes:Number($('w-u-min').value||0), reps:Number($('w-u-reps').value||0), comment:$('w-u-comment').value };
            const r = await api(`/api/workouts/${id}`, 'PUT', body); show('w-out-update', r.data);
        });
        $('w-delete')?.addEventListener('click', async () => { const id = $('w-d-id').value.trim(); if(!id) return; const r = await api(`/api/workouts/${id}`, 'DELETE'); show('w-out-delete', r.ok?{deleted:id}:r.data); });
    }

    // Advice
    if (document.body.id === 'advice-page') {
        $('a-list')?.addEventListener('click', async () => { const r = await api('/api/advice'); show('a-out-list', r.data); });
        $('a-one-btn')?.addEventListener('click', async () => { const id = $('a-one-id').value.trim(); if(!id) return; const r = await api(`/api/advice/${id}`); show('a-out-one', r.data); });
        $('a-create')?.addEventListener('click', async () => {
            const r = await api('/api/advice', 'POST', { text:$('a-c-text').value, userId:Number($('a-c-user').value||0) });
            show('a-out-create', r.data);
        });
    }

    // Templates
    if (document.body.id === 'templates-page') {
        $('t-list')?.addEventListener('click', async () => { const r = await api('/api/templates'); show('t-out-list', r.data); });
        $('t-one-btn')?.addEventListener('click', async () => { const id = $('t-one-id').value.trim(); if(!id) return; const r = await api(`/api/templates/${id}`); show('t-out-one', r.data); });
        $('t-create')?.addEventListener('click', async () => {
            const r = await api('/api/templates', 'POST', { name:$('t-c-name').value, description:$('t-c-desc').value });
            show('t-out-create', r.data);
        });
    }
});
