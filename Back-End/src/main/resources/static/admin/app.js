(function () {
  "use strict";

  const API = ""; // same origin as the served page
  let authHeader = null;

  // ---- helpers -------------------------------------------------------------
  const $ = (id) => document.getElementById(id);

  function show(el) { el.classList.remove("hidden"); }
  function hide(el) { el.classList.add("hidden"); }

  function money(n) {
    const v = Number(n || 0);
    return "$" + v.toFixed(2);
  }

  async function api(path, options) {
    options = options || {};
    options.headers = Object.assign(
      { "Content-Type": "application/json" },
      options.headers || {}
    );
    if (authHeader) options.headers["Authorization"] = authHeader;
    const res = await fetch(API + path, options);
    return res;
  }

  function flash(el, message, ok) {
    el.textContent = message;
    el.className = "alert " + (ok ? "alert-ok" : "alert-error");
    show(el);
    setTimeout(() => hide(el), 3500);
  }

  // ---- auth ----------------------------------------------------------------
  async function login() {
    const u = $("username").value.trim();
    const p = $("password").value;
    hide($("loginError"));
    if (!u || !p) {
      flash($("loginError"), "Enter username and password.", false);
      return;
    }
    const header = "Basic " + btoa(u + ":" + p);
    const res = await fetch(API + "/api/auth/me", { headers: { Authorization: header } });
    if (!res.ok) {
      flash($("loginError"), "Invalid credentials (" + res.status + ").", false);
      return;
    }
    const me = await res.json();
    if (!me.roles || me.roles.indexOf("ROLE_ADMIN") === -1) {
      flash($("loginError"), "This account is not an administrator.", false);
      return;
    }
    authHeader = header;
    $("who").textContent = me.username + " · admin";
    hide($("loginView"));
    show($("dashboard"));
    show($("session"));
    loadProducts();
    loadReport();
  }

  function logout() {
    authHeader = null;
    $("password").value = "";
    show($("loginView"));
    hide($("dashboard"));
    hide($("session"));
  }

  // ---- products ------------------------------------------------------------
  async function loadProducts() {
    const res = await api("/api/products");
    const products = res.ok ? await res.json() : [];
    const rows = $("productRows");
    rows.innerHTML = "";
    const select = $("buy-product");
    select.innerHTML = "";
    products.forEach((p) => {
      const tr = document.createElement("tr");
      tr.innerHTML =
        "<td>" + p.id + "</td>" +
        "<td>" + escapeHtml(p.name) + "</td>" +
        "<td>" + money(p.price) + "</td>" +
        "<td>" + (p.quantity ?? "") + "</td>" +
        "<td></td>";
      const actions = tr.lastChild;
      const edit = button("Edit", "btn btn-ghost btn-sm", () => fillForm(p));
      const del = button("Delete", "btn btn-danger btn-sm", () => deleteProduct(p.id));
      actions.appendChild(edit);
      actions.appendChild(del);
      rows.appendChild(tr);

      const opt = document.createElement("option");
      opt.value = p.id;
      opt.textContent = p.name;
      select.appendChild(opt);
    });
  }

  function fillForm(p) {
    $("formTitle").textContent = "Edit product #" + p.id;
    $("productId").value = p.id;
    $("p-name").value = p.name || "";
    $("p-description").value = p.description || "";
    $("p-price").value = p.price != null ? p.price : "";
    $("p-quantity").value = p.quantity != null ? p.quantity : "";
    $("p-image").value = p.image || "";
  }

  function resetForm() {
    $("formTitle").textContent = "Add product";
    $("productId").value = "";
    ["p-name", "p-description", "p-price", "p-quantity", "p-image"].forEach((id) => ($(id).value = ""));
  }

  async function saveProduct() {
    const id = $("productId").value;
    const body = {
      name: $("p-name").value.trim(),
      description: $("p-description").value.trim(),
      price: parseFloat($("p-price").value),
      quantity: parseInt($("p-quantity").value, 10),
      image: $("p-image").value.trim(),
    };
    let res;
    if (id) {
      res = await api("/api/products/" + id, { method: "PATCH", body: JSON.stringify(body) });
    } else {
      res = await api("/api/products", { method: "POST", body: JSON.stringify(body) });
    }
    if (res.ok) {
      flash($("productMsg"), id ? "Product updated." : "Product created.", true);
      resetForm();
      loadProducts();
    } else {
      flash($("productMsg"), "Save failed (" + res.status + ").", false);
    }
  }

  async function deleteProduct(id) {
    if (!confirm("Delete product #" + id + "?")) return;
    const res = await api("/api/products/" + id, { method: "DELETE" });
    if (res.ok) {
      flash($("productMsg"), "Product deleted.", true);
      loadProducts();
    } else {
      flash($("productMsg"), "Delete failed (" + res.status + ").", false);
    }
  }

  // ---- purchases / report --------------------------------------------------
  async function recordPurchase() {
    const productId = parseInt($("buy-product").value, 10);
    const quantity = parseInt($("buy-qty").value, 10);
    if (!productId) {
      flash($("purchaseMsg"), "Select a product first.", false);
      return;
    }
    const res = await api("/api/purchases", {
      method: "POST",
      body: JSON.stringify({ productId: productId, quantity: quantity }),
    });
    if (res.ok) {
      flash($("purchaseMsg"), "Purchase recorded.", true);
      loadProducts();
      loadReport();
    } else {
      const err = await res.json().catch(() => ({}));
      flash($("purchaseMsg"), err.message || "Purchase failed (" + res.status + ").", false);
    }
  }

  async function loadReport() {
    const res = await api("/api/purchases/daily-report");
    const data = res.ok ? await res.json() : [];
    const rows = $("reportRows");
    rows.innerHTML = "";
    data.forEach((r) => {
      const tr = document.createElement("tr");
      tr.innerHTML =
        "<td>" + r.date + "</td>" +
        "<td>" + escapeHtml(r.productName) + "</td>" +
        "<td>" + r.totalQuantity + "</td>" +
        "<td>" + money(r.totalRevenue) + "</td>";
      rows.appendChild(tr);
    });
    renderChart(data);
  }

  function renderChart(data) {
    const chart = $("chart");
    chart.innerHTML = "";
    if (!data.length) {
      chart.innerHTML = '<p class="muted">No purchases recorded yet.</p>';
      return;
    }
    const max = Math.max.apply(null, data.map((d) => Number(d.totalRevenue)));
    data.slice(0, 12).forEach((d) => {
      const bar = document.createElement("div");
      bar.className = "bar";
      const pct = max > 0 ? (Number(d.totalRevenue) / max) * 100 : 0;
      bar.innerHTML =
        '<span class="val">' + money(d.totalRevenue) + "</span>" +
        '<div class="fill" style="height:' + pct + '%"></div>' +
        '<span class="cap">' + escapeHtml(d.productName) + "</span>";
      chart.appendChild(bar);
    });
  }

  // ---- misc ----------------------------------------------------------------
  function button(text, cls, onClick) {
    const b = document.createElement("button");
    b.textContent = text;
    b.className = cls;
    b.addEventListener("click", onClick);
    return b;
  }

  function escapeHtml(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  function switchTab(name) {
    document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t.dataset.tab === name));
    $("tab-products").classList.toggle("hidden", name !== "products");
    $("tab-report").classList.toggle("hidden", name !== "report");
  }

  // ---- wiring --------------------------------------------------------------
  document.addEventListener("DOMContentLoaded", function () {
    $("loginBtn").addEventListener("click", login);
    $("password").addEventListener("keydown", (e) => { if (e.key === "Enter") login(); });
    $("logoutBtn").addEventListener("click", logout);
    $("saveProductBtn").addEventListener("click", saveProduct);
    $("resetFormBtn").addEventListener("click", resetForm);
    $("refreshProducts").addEventListener("click", loadProducts);
    $("recordPurchaseBtn").addEventListener("click", recordPurchase);
    $("refreshReport").addEventListener("click", loadReport);
    document.querySelectorAll(".tab").forEach((t) => t.addEventListener("click", () => switchTab(t.dataset.tab)));
  });
})();
