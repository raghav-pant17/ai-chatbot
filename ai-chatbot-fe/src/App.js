import React, { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import {
  Bot,
  ChevronDown,
  ChevronRight,
  LogOut,
  Minus,
  PackageCheck,
  Plus,
  RefreshCw,
  Send,
  ShoppingCart,
  Store,
  Ticket,
  TicketPlus,
  UserCircle,
  Wallet,
} from 'lucide-react';
import { Alert } from './components/ui/alert';
import { Badge } from './components/ui/badge';
import { Button } from './components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from './components/ui/card';
import { Input } from './components/ui/input';
import { Label } from './components/ui/label';
import { Textarea } from './components/ui/textarea';
import { cn } from './lib/utils';

const API_BASE_URL = 'http://localhost:8080';
const WS_URL = `${API_BASE_URL}/ws`;
const SEND_ENDPOINT = '/app/chat.sendMessage';
const SEND_ADMIN_REPLY_ENDPOINT = '/app/admin.reply';
const CUSTOMER_CHAT_QUEUE = '/user/queue/chat';
const ADMIN_TICKET_TOPIC = '/topic/admin/tickets';

function App() {
  const isAdminRoute = window.location.pathname.replace(/\/+$/, '') === '/admin';
  return isAdminRoute ? <AdminApp /> : <CustomerApp />;
}

function CustomerApp() {
  const savedSession = readSavedSession();
  const [authMode, setAuthMode] = useState('login');
  const [loginUsername, setLoginUsername] = useState('demo.customer');
  const [loginPassword, setLoginPassword] = useState('');
  const [signupUsername, setSignupUsername] = useState('');
  const [signupFullName, setSignupFullName] = useState('');
  const [signupEmail, setSignupEmail] = useState('');
  const [signupPassword, setSignupPassword] = useState('');
  const [pendingVerificationEmail, setPendingVerificationEmail] = useState('');
  const [verificationCode, setVerificationCode] = useState('');
  const [verificationMessage, setVerificationMessage] = useState('');
  const [user, setUser] = useState(savedSession?.user || null);
  const [token, setToken] = useState(savedSession?.token || '');
  const [loginError, setLoginError] = useState('');
  const [isLoggingIn, setIsLoggingIn] = useState(false);
  const [activePage, setActivePage] = useState(savedSession?.user ? 'support' : 'shop');

  const [orders, setOrders] = useState([]);
  const [ordersError, setOrdersError] = useState('');
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [expandedOrderId, setExpandedOrderId] = useState('');
  const [selectedOrderId, setSelectedOrderId] = useState('');
  const [orderDetails, setOrderDetails] = useState({});
  const [detailLoadingId, setDetailLoadingId] = useState('');

  const [createdTickets, setCreatedTickets] = useState([]);
  const [ticketsLoading, setTicketsLoading] = useState(false);
  const [ticketsError, setTicketsError] = useState('');
  const [selectedTicketSummaryId, setSelectedTicketSummaryId] = useState(null);
  const [isStartingTicket, setIsStartingTicket] = useState(false);
  const [ticketDetailLoading, setTicketDetailLoading] = useState(false);

  const [connected, setConnected] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [waitingForReply, setWaitingForReply] = useState(false);
  const [ticketId, setTicketId] = useState(null);
  const [ticketStatus, setTicketStatus] = useState('');
  const [conversationState, setConversationState] = useState('START');

  const [products, setProducts] = useState([]);
  const [productsLoading, setProductsLoading] = useState(false);
  const [productsError, setProductsError] = useState('');
  const [cart, setCart] = useState({});
  const [budget, setBudget] = useState(savedSession?.user ? userBudget(savedSession.user) : emptyBudget());
  const [orderError, setOrderError] = useState('');
  const [orderMessage, setOrderMessage] = useState('');
  const [isPlacingOrder, setIsPlacingOrder] = useState(false);

  const clientRef = useRef(null);
  const userRef = useRef(user);
  const tokenRef = useRef(token);
  const activeTicketRef = useRef(ticketId || selectedTicketSummaryId);
  const messagesEndRef = useRef(null);

  useEffect(() => {
    userRef.current = user;
  }, [user]);

  useEffect(() => {
    tokenRef.current = token;
  }, [token]);

  useEffect(() => {
    activeTicketRef.current = ticketId || selectedTicketSummaryId;
  }, [ticketId, selectedTicketSummaryId]);

  useEffect(() => {
    fetchProducts();
  }, []);

  useEffect(() => {
    if (!user || !token) return undefined;
    connectWebSocket();

    return () => {
      clientRef.current?.deactivate();
      clientRef.current = null;
    };
  }, [user?.userId, token]);

  useEffect(() => {
    if (!user || !token) return;
    fetchOrders(user.userId, token);
    fetchCustomerTickets(user.userId, token);
    fetchBudget(user.userId, token);
  }, [user?.userId, token]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  async function login(event) {
    event.preventDefault();
    const nextUsername = loginUsername.trim();
    if (!nextUsername || !loginPassword) {
      setLoginError('Enter your username and password.');
      return;
    }

    setIsLoggingIn(true);
    setLoginError('');

    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: nextUsername, password: loginPassword }),
      });

      if (response.status === 401) {
        setLoginError('Username or password is incorrect.');
        return;
      }

      if (response.status === 403) {
        setLoginError('Please verify your email before logging in.');
        return;
      }

      if (!response.ok) {
        setLoginError('Login failed. Check that the backend is running.');
        return;
      }

      const data = await response.json();
      const nextUser = normalizeCustomer(data);

      saveCustomerSession(nextUser, data.accessToken);
      setUser(nextUser);
      setToken(data.accessToken);
      setBudget(userBudget(nextUser));
      setActivePage('support');
      setLoginPassword('');
      resetConversation();
    } catch (error) {
      setLoginError('Could not reach the backend at http://localhost:8080.');
    } finally {
      setIsLoggingIn(false);
    }
  }

  async function signup(event) {
    event.preventDefault();
    const payload = {
      username: signupUsername.trim(),
      fullName: signupFullName.trim(),
      email: signupEmail.trim(),
      password: signupPassword,
    };

    if (!payload.username || !payload.fullName || !payload.email || !payload.password) {
      setLoginError('Fill in all signup fields.');
      return;
    }

    setIsLoggingIn(true);
    setLoginError('');

    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/signup`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (response.status === 409) {
        setLoginError('That username or email is already in use.');
        return;
      }

      if (response.status === 400) {
        setLoginError('Check the signup details and use a password with at least 6 characters.');
        return;
      }

      if (!response.ok) {
        setLoginError('Signup failed. Check that the backend is running.');
        return;
      }

      const data = await response.json();
      setPendingVerificationEmail(data.email || payload.email);
      setVerificationMessage(data.message || `Verification code sent to ${payload.email}.`);
      setVerificationCode('');
      setAuthMode('verify');
      setSignupPassword('');
      resetConversation();
    } catch (error) {
      setLoginError('Could not reach the backend at http://localhost:8080.');
    } finally {
      setIsLoggingIn(false);
    }
  }

  async function verifyEmail(event) {
    event.preventDefault();
    const email = pendingVerificationEmail.trim();
    const code = verificationCode.trim();

    if (!email || !code) {
      setLoginError('Enter the verification code from your email.');
      return;
    }

    setIsLoggingIn(true);
    setLoginError('');

    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/verify-email`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, code }),
      });

      if (response.status === 400) {
        setLoginError('Verification code is invalid or expired.');
        return;
      }

      if (!response.ok) {
        setLoginError('Email verification failed.');
        return;
      }

      const data = await response.json();
      const nextUser = normalizeCustomer(data);

      saveCustomerSession(nextUser, data.accessToken);
      setUser(nextUser);
      setToken(data.accessToken);
      setBudget(userBudget(nextUser));
      setActivePage('shop');
      setVerificationCode('');
      setPendingVerificationEmail('');
      setVerificationMessage('');
      resetConversation();
    } catch (error) {
      setLoginError('Could not reach the backend at http://localhost:8080.');
    } finally {
      setIsLoggingIn(false);
    }
  }

  async function resendVerificationCode() {
    const email = pendingVerificationEmail.trim();
    if (!email) return;

    setIsLoggingIn(true);
    setLoginError('');

    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/resend-verification`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email }),
      });

      if (!response.ok) {
        setLoginError('Could not resend the verification code.');
        return;
      }

      const data = await response.json();
      setVerificationMessage(data.message || 'A new verification code was sent.');
      setVerificationCode('');
    } catch (error) {
      setLoginError('Could not reach the backend at http://localhost:8080.');
    } finally {
      setIsLoggingIn(false);
    }
  }

  async function logout() {
    if (token) {
      fetch(`${API_BASE_URL}/api/auth/logout`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      }).catch(() => {});
    }

    sessionStorage.removeItem('support-chat-session');
    clientRef.current?.deactivate();
    clientRef.current = null;
    setUser(null);
    setToken('');
    setOrders([]);
    setOrderDetails({});
    setSelectedOrderId('');
    setExpandedOrderId('');
    setBudget(emptyBudget());
    setCart({});
    setOrderError('');
    setOrderMessage('');
    setActivePage('shop');
    setCreatedTickets([]);
    setSelectedTicketSummaryId(null);
    resetConversation();
  }

  function resetConversation() {
    setMessages([]);
    setDraft('');
    setWaitingForReply(false);
    setTicketId(null);
    setTicketStatus('');
    setConversationState('START');
  }

  async function fetchProducts() {
    setProductsLoading(true);
    setProductsError('');

    try {
      const response = await fetch(`${API_BASE_URL}/api/products`);
      if (!response.ok) {
        setProductsError('Products could not be loaded right now.');
        return;
      }

      const data = await response.json();
      setProducts(Array.isArray(data) ? data : []);
    } catch (error) {
      setProductsError('Could not reach the product catalog.');
    } finally {
      setProductsLoading(false);
    }
  }

  async function fetchBudget(userId, accessToken) {
    try {
      const response = await authorizedFetch(`/api/users/${encodeURIComponent(userId)}/orders/budget`, accessToken);

      if (response.status === 401) {
        handleExpiredSession();
        return;
      }

      if (!response.ok) return;

      const data = await response.json();
      setBudget(data);
      setUser((currentUser) => {
        if (!currentUser) return currentUser;
        const nextUser = { ...currentUser, ...data };
        saveCustomerSession(nextUser, accessToken);
        return nextUser;
      });
    } catch (error) {
      // Budget is displayed as a convenience; order creation still validates server-side.
    }
  }

  function setCartQuantity(productId, quantity) {
    setOrderError('');
    setOrderMessage('');
    setCart((previous) => {
      const nextQuantity = Math.max(0, Math.min(9, Number(quantity) || 0));
      const nextCart = { ...previous };
      if (nextQuantity === 0) {
        delete nextCart[productId];
      } else {
        nextCart[productId] = nextQuantity;
      }
      return nextCart;
    });
  }

  async function placeOrder() {
    if (!user || !token || isPlacingOrder) return;

    const items = Object.entries(cart)
      .filter(([, quantity]) => quantity > 0)
      .map(([productId, quantity]) => ({ productId, quantity }));

    if (!items.length) {
      setOrderError('Select at least one item.');
      return;
    }

    setIsPlacingOrder(true);
    setOrderError('');
    setOrderMessage('');

    try {
      const response = await authorizedFetch(`/api/users/${encodeURIComponent(user.userId)}/orders`, token, {
        method: 'POST',
        body: JSON.stringify({ items }),
      });

      if (response.status === 401) {
        handleExpiredSession();
        return;
      }

      if (response.status === 400) {
        setOrderError('This cart is over your remaining budget. Remove an item and try again.');
        await fetchBudget(user.userId, token);
        return;
      }

      if (!response.ok) {
        setOrderError('Order could not be placed right now.');
        return;
      }

      const data = await response.json();
      if (data.budget) {
        setBudget(data.budget);
        setUser((currentUser) => {
          if (!currentUser) return currentUser;
          const nextUser = { ...currentUser, ...data.budget };
          saveCustomerSession(nextUser, token);
          return nextUser;
        });
      }
      setCart({});
      setOrderMessage(`Order ${data.order.orderId} placed successfully.`);
      await fetchOrders(user.userId, token);
      setExpandedOrderId(data.order.orderId);
      setSelectedOrderId(data.order.orderId);
      setOrderDetails((previous) => ({ ...previous, [data.order.orderId]: data.order }));
      setActivePage('support');
    } catch (error) {
      setOrderError('Could not reach the order API.');
    } finally {
      setIsPlacingOrder(false);
    }
  }

  async function fetchOrders(userId, accessToken) {
    setOrdersLoading(true);
    setOrdersError('');

    try {
      const response = await authorizedFetch(`/api/users/${encodeURIComponent(userId)}/orders`, accessToken);

      if (response.status === 401) {
        handleExpiredSession();
        return;
      }

      if (response.status === 403) {
        setOrdersError('You do not have permission to view these orders.');
        return;
      }

      if (!response.ok) {
        setOrdersError('Orders could not be loaded right now.');
        return;
      }

      const data = await response.json();
      setOrders(data);
      if (data.length > 0 && !selectedOrderId) {
        setSelectedOrderId(data[0].orderId);
      }
    } catch (error) {
      setOrdersError('Could not reach the order API.');
    } finally {
      setOrdersLoading(false);
    }
  }

  async function fetchCustomerTickets(userId = user?.userId, accessToken = token) {
    if (!userId || !accessToken) return;

    setTicketsLoading(true);
    setTicketsError('');

    try {
      const response = await authorizedFetch(`/api/users/${encodeURIComponent(userId)}/tickets`, accessToken);

      if (response.status === 401) {
        handleExpiredSession();
        return;
      }

      if (response.status === 403) {
        setTicketsError('You do not have permission to view these tickets.');
        return;
      }

      if (!response.ok) {
        setTicketsError('Tickets could not be loaded right now.');
        return;
      }

      const data = await response.json();
      setCreatedTickets(Array.isArray(data) ? data : []);
    } catch (error) {
      setTicketsError('Could not reach the ticket API.');
    } finally {
      setTicketsLoading(false);
    }
  }

  async function startNewTicket() {
    if (!user || !token || isStartingTicket) return;

    setIsStartingTicket(true);
    setTicketsError('');

    try {
      const response = await authorizedFetch(`/api/users/${encodeURIComponent(user.userId)}/tickets`, token, {
        method: 'POST',
      });

      if (response.status === 401) {
        handleExpiredSession();
        return;
      }

      if (!response.ok) {
        setTicketsError('A ticket could not be created right now.');
        return;
      }

      const data = await response.json();
      const ticket = data.ticket;
      resetConversation();
      setTicketId(ticket.ticketId);
      setTicketStatus(ticket.status || 'OPEN');
      setConversationState(ticket.state || 'SELECT_ITEMS');
      setSelectedTicketSummaryId(ticket.ticketId);
      setWaitingForReply(false);
      setMessages([
        {
          id: `${Date.now()}-ticket-created`,
          type: 'bot',
          content: data.message || `Ticket #${ticket.ticketId} was created.`,
          timestamp: new Date().toISOString(),
        },
      ]);
      await fetchCustomerTickets(user.userId, token);
    } catch (error) {
      setTicketsError('Could not create a ticket right now.');
    } finally {
      setIsStartingTicket(false);
    }
  }

  async function openCustomerTicket(ticket) {
    if (!user || !token || !ticket?.ticketId) return;

    setTicketDetailLoading(true);
    setTicketsError('');
    setSelectedTicketSummaryId(ticket.ticketId);

    try {
      const response = await authorizedFetch(
        `/api/users/${encodeURIComponent(user.userId)}/tickets/${encodeURIComponent(ticket.ticketId)}`,
        token
      );

      if (response.status === 401) {
        handleExpiredSession();
        return;
      }

      if (response.status === 404) {
        setTicketsError(`Ticket #${ticket.ticketId} could not be found.`);
        return;
      }

      if (!response.ok) {
        setTicketsError(`Ticket #${ticket.ticketId} conversation could not be loaded.`);
        return;
      }

      const detail = await response.json();
      setTicketId(detail.ticketId);
      setTicketStatus(detail.status || '');
      setConversationState(detail.state || 'START');
      setWaitingForReply(false);
      setMessages(mapTicketMessages(detail.messages));
      await fetchCustomerTickets(user.userId, token);
    } catch (error) {
      setTicketsError(`Ticket #${ticket.ticketId} conversation could not be loaded.`);
    } finally {
      setTicketDetailLoading(false);
    }
  }

  async function toggleOrder(orderId) {
    const nextExpanded = expandedOrderId === orderId ? '' : orderId;
    setExpandedOrderId(nextExpanded);
    setSelectedOrderId(orderId);

    if (!nextExpanded) return;
    await loadOrderDetail(orderId);
  }

  async function loadOrderDetail(orderId, options = {}) {
    const { force = false, quiet = false } = options;
    const currentUser = userRef.current;
    const currentToken = tokenRef.current;

    if (!orderId || !currentUser || !currentToken) return;
    if (!force && orderDetails[orderId]) return;

    setDetailLoadingId(orderId);
    if (!quiet) {
      setOrdersError('');
    }

    try {
      const response = await authorizedFetch(
        `/api/users/${encodeURIComponent(currentUser.userId)}/orders/${encodeURIComponent(orderId)}`,
        currentToken
      );

      if (response.status === 401) {
        handleExpiredSession();
        return;
      }

      if (response.status === 403) {
        if (!quiet) {
          setOrdersError('You do not have permission to view this order.');
        }
        return;
      }

      if (!response.ok) {
        if (!quiet) {
          setOrdersError(`Could not load items for ${orderId}.`);
        }
        return;
      }

      const data = await response.json();
      setOrderDetails((previous) => ({ ...previous, [orderId]: data }));
    } catch (error) {
      if (!quiet) {
        setOrdersError(`Could not load items for ${orderId}.`);
      }
    } finally {
      setDetailLoadingId((current) => (current === orderId ? '' : current));
    }
  }

  function connectWebSocket() {
    if (clientRef.current?.active) return;

    setConnecting(true);
    const stompClient = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: {
        Authorization: `Bearer ${tokenRef.current}`,
      },
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        setConnected(true);
        setConnecting(false);
        stompClient.subscribe(CUSTOMER_CHAT_QUEUE, (frame) => {
          try {
            const response = JSON.parse(frame.body);
            receiveBotMessage(response);
          } catch (error) {
            console.error('Invalid chat response', error);
          }
        });
      },
      onDisconnect: () => {
        setConnected(false);
        setConnecting(false);
      },
      onWebSocketClose: () => {
        setConnected(false);
        setConnecting(false);
      },
      onStompError: () => {
        setConnected(false);
        setConnecting(false);
      },
    });

    clientRef.current = stompClient;
    stompClient.activate();
  }

  function receiveBotMessage(response) {
    if (response.sender === 'USER') {
      return;
    }

    const activeTicketId = activeTicketRef.current;
    if (response.ticketId && activeTicketId && response.ticketId !== activeTicketId) {
      fetchCustomerTickets(userRef.current?.userId, tokenRef.current);
      return;
    }

    setWaitingForReply(false);

    if (response.message?.trim()) {
      setMessages((previous) => [
        ...previous,
        {
          id: `${response.timestamp || Date.now()}-bot`,
          type: 'bot',
          content: response.message,
          timestamp: response.timestamp || new Date().toISOString(),
        },
      ]);
    }

    setTicketId(response.ticketId ?? null);
    setTicketStatus(response.status || '');
    setConversationState(response.state || 'START');

    if (response.orderId && response.status === 'RESOLVED') {
      loadOrderDetail(response.orderId, { force: true, quiet: true });
    }

    if (response.ticketId) {
      setSelectedTicketSummaryId(response.ticketId);
      fetchCustomerTickets(userRef.current?.userId, tokenRef.current);
    }
  }

  function sendMessage(messageText) {
    const text = messageText.trim();
    if (!text || !userRef.current) return;
    if (ticketStatus === 'RESOLVED' || conversationState === 'RESOLVED') {
      setDraft('');
      return;
    }

    setMessages((previous) => [
      ...previous,
      {
        id: `${Date.now()}-user`,
        type: 'user',
        content: text,
        timestamp: new Date().toISOString(),
      },
    ]);
    setDraft('');

    if (!clientRef.current?.connected) {
      connectWebSocket();
      setMessages((previous) => [
        ...previous,
        {
          id: `${Date.now()}-system`,
          type: 'bot',
          content: 'Chat is reconnecting. Please send that message again in a moment.',
          timestamp: new Date().toISOString(),
        },
      ]);
      setWaitingForReply(false);
      return;
    }

    setWaitingForReply(true);
    clientRef.current.publish({
      destination: SEND_ENDPOINT,
      body: JSON.stringify({
        userId: userRef.current.userId,
        message: text,
      }),
    });
  }

  function submitMessage(event) {
    event.preventDefault();
    sendMessage(draft);
  }

  const ticketIsResolved = ticketStatus === 'RESOLVED' || conversationState === 'RESOLVED';
  const hasActiveTicket = Boolean(ticketId || selectedTicketSummaryId);
  const cartTotal = calculateCartTotal(products, cart);
  const cartCount = Object.values(cart).reduce((total, quantity) => total + quantity, 0);

  function handleExpiredSession() {
    sessionStorage.removeItem('support-chat-session');
    clientRef.current?.deactivate();
    clientRef.current = null;
    setUser(null);
    setToken('');
    setLoginError('Your session expired. Please log in again.');
  }

  if (!user) {
    return (
      <main className="login-page">
        <Card className="login-panel" aria-labelledby="login-title">
          <CardHeader>
            <Badge variant="outline">Customer store</Badge>
            <CardTitle id="login-title">Shop and support</CardTitle>
            <CardDescription>Log in or create an account to order items and manage support tickets.</CardDescription>
          </CardHeader>

          <CardContent>
            <div className="segmented-control" role="tablist" aria-label="Authentication options">
              <button
                type="button"
                className={cn(authMode === 'login' && 'active')}
                onClick={() => {
                  setAuthMode('login');
                  setLoginError('');
                }}
              >
                Log in
              </button>
              <button
                type="button"
                className={cn(authMode === 'signup' && 'active')}
                onClick={() => {
                  setAuthMode('signup');
                  setLoginError('');
                }}
              >
                Sign up
              </button>
            </div>

            {authMode === 'login' ? (
              <form className="stack-form" onSubmit={login}>
                <div className="field">
                  <Label htmlFor="username">Username</Label>
                  <Input
                    id="username"
                    type="text"
                    value={loginUsername}
                    onChange={(event) => setLoginUsername(event.target.value)}
                    placeholder="demo.customer"
                    autoComplete="username"
                  />
                </div>
                <div className="field">
                  <Label htmlFor="customerPassword">Password</Label>
                  <Input
                    id="customerPassword"
                    type="password"
                    value={loginPassword}
                    onChange={(event) => setLoginPassword(event.target.value)}
                    placeholder="password123"
                    autoComplete="current-password"
                  />
                  <p className="hint">Demo customer: demo.customer / password123</p>
                </div>
                {loginError && <Alert variant="destructive">{loginError}</Alert>}
                <Button type="submit" disabled={isLoggingIn}>
                  <UserCircle size={16} />
                  {isLoggingIn ? 'Logging in...' : 'Log in'}
                </Button>
              </form>
            ) : authMode === 'signup' ? (
              <form className="stack-form" onSubmit={signup}>
                <div className="field">
                  <Label htmlFor="signupUsername">Username</Label>
                  <Input
                    id="signupUsername"
                    type="text"
                    value={signupUsername}
                    onChange={(event) => setSignupUsername(event.target.value)}
                    placeholder="your.name"
                    autoComplete="username"
                  />
                </div>
                <div className="field">
                  <Label htmlFor="signupFullName">Full name</Label>
                  <Input
                    id="signupFullName"
                    type="text"
                    value={signupFullName}
                    onChange={(event) => setSignupFullName(event.target.value)}
                    placeholder="Your Name"
                    autoComplete="name"
                  />
                </div>
                <div className="field">
                  <Label htmlFor="signupEmail">Email</Label>
                  <Input
                    id="signupEmail"
                    type="email"
                    value={signupEmail}
                    onChange={(event) => setSignupEmail(event.target.value)}
                    placeholder="you@example.com"
                    autoComplete="email"
                  />
                </div>
                <div className="field">
                  <Label htmlFor="signupPassword">Password</Label>
                  <Input
                    id="signupPassword"
                    type="password"
                    value={signupPassword}
                    onChange={(event) => setSignupPassword(event.target.value)}
                    placeholder="At least 6 characters"
                    autoComplete="new-password"
                  />
                  <p className="hint">New customers receive a {formatMoney(15000)} starter budget.</p>
                </div>
                {loginError && <Alert variant="destructive">{loginError}</Alert>}
                <Button type="submit" disabled={isLoggingIn}>
                  <UserCircle size={16} />
                  {isLoggingIn ? 'Creating account...' : 'Create account'}
                </Button>
              </form>
            ) : (
              <form className="stack-form" onSubmit={verifyEmail}>
                <div className="field">
                  <Label htmlFor="verificationEmail">Email</Label>
                  <Input
                    id="verificationEmail"
                    type="email"
                    value={pendingVerificationEmail}
                    onChange={(event) => setPendingVerificationEmail(event.target.value)}
                    autoComplete="email"
                  />
                </div>
                <div className="field">
                  <Label htmlFor="verificationCode">Verification code</Label>
                  <Input
                    id="verificationCode"
                    type="text"
                    inputMode="numeric"
                    maxLength="6"
                    value={verificationCode}
                    onChange={(event) => setVerificationCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
                    placeholder="123456"
                    autoComplete="one-time-code"
                  />
                  <p className="hint">{verificationMessage || 'Enter the 6-digit code sent to your email.'}</p>
                </div>
                {loginError && <Alert variant="destructive">{loginError}</Alert>}
                <Button type="submit" disabled={isLoggingIn || verificationCode.length !== 6}>
                  <UserCircle size={16} />
                  {isLoggingIn ? 'Verifying...' : 'Verify email'}
                </Button>
                <Button type="button" variant="outline" onClick={resendVerificationCode} disabled={isLoggingIn || !pendingVerificationEmail}>
                  Resend code
                </Button>
              </form>
            )}
          </CardContent>
        </Card>
      </main>
    );
  }

  return (
    <div className="app-shell">
      <header className="top-bar">
        <div className="title-group">
          <Badge variant="outline">Customer store</Badge>
          <h1>{activePage === 'shop' ? 'Initiate order' : 'Orders and support'}</h1>
        </div>
        <div className="header-meta">
          <nav className="page-tabs" aria-label="Customer pages">
            <Button type="button" variant={activePage === 'shop' ? 'default' : 'outline'} size="sm" onClick={() => setActivePage('shop')}>
              <Store size={15} />
              Initiate order
            </Button>
            <Button type="button" variant={activePage === 'support' ? 'default' : 'outline'} size="sm" onClick={() => setActivePage('support')}>
              <PackageCheck size={15} />
              Orders & support
            </Button>
          </nav>
          {activePage === 'support' && (
            <StatusPill label={connected ? 'Connected' : connecting ? 'Connecting' : 'Disconnected'} tone={connected ? 'success' : 'danger'} />
          )}
          {ticketId && <StatusPill label={`Ticket #${ticketId}`} tone="info" />}
          {activePage === 'support' && <StatusPill label={ticketStatus || 'No active ticket'} tone={statusTone(ticketStatus)} />}
          <Button type="button" variant="outline" onClick={logout}>
            <LogOut size={16} />
            Logout
          </Button>
        </div>
      </header>

      <main className="customer-page">
        <section className="customer-strip">
          <Stat icon={UserCircle} label="Signed in" value={user.fullName} detail={user.email} />
          <Stat icon={Wallet} label="Remaining budget" value={formatMoney(budget.remainingBudget)} detail={`${formatMoney(budget.spentAmount)} spent`} />
        </section>

        {activePage === 'shop' ? (
          <ProductShopPanel
            products={products}
            loading={productsLoading}
            error={productsError}
            cart={cart}
            cartTotal={cartTotal}
            cartCount={cartCount}
            budget={budget}
            orderError={orderError}
            orderMessage={orderMessage}
            isPlacingOrder={isPlacingOrder}
            onRefresh={fetchProducts}
            onSetQuantity={setCartQuantity}
            onPlaceOrder={placeOrder}
            onViewOrders={() => setActivePage('support')}
          />
        ) : (
          <>
            <div className="customer-grid">
              <OrdersPanel
                orders={orders}
                ordersLoading={ordersLoading}
                ordersError={ordersError}
                selectedOrderId={selectedOrderId}
                expandedOrderId={expandedOrderId}
                orderDetails={orderDetails}
                detailLoadingId={detailLoadingId}
                onRefresh={() => fetchOrders(user.userId, token)}
                onCreateOrder={() => setActivePage('shop')}
                onToggleOrder={toggleOrder}
              />

              <TicketsPanel
                tickets={createdTickets}
                loading={ticketsLoading}
                error={ticketsError}
                activeTicketId={ticketId || selectedTicketSummaryId}
                isStarting={isStartingTicket}
                onCreate={startNewTicket}
                onRefresh={() => fetchCustomerTickets(user.userId, token)}
                onSelect={openCustomerTicket}
              />
            </div>

            {hasActiveTicket && (
              <ChatPanel
                messages={messages}
                draft={draft}
                setDraft={setDraft}
                onSubmit={submitMessage}
                connected={connected}
                waitingForReply={waitingForReply}
                ticketId={ticketId}
                isResolved={ticketIsResolved}
                loading={ticketDetailLoading}
                messagesEndRef={messagesEndRef}
              />
            )}
          </>
        )}
      </main>
    </div>
  );
}

function ProductShopPanel({
  products,
  loading,
  error,
  cart,
  cartTotal,
  cartCount,
  budget,
  orderError,
  orderMessage,
  isPlacingOrder,
  onRefresh,
  onSetQuantity,
  onPlaceOrder,
  onViewOrders,
}) {
  const overBudget = cartTotal > Number(budget.remainingBudget || 0);

  return (
    <div className="shop-layout">
      <Card aria-labelledby="catalog-title">
        <CardHeader className="section-heading">
          <div>
            <CardTitle id="catalog-title">Items</CardTitle>
            <CardDescription>Select products within your remaining shopping budget.</CardDescription>
          </div>
          <Button type="button" variant="outline" size="sm" onClick={onRefresh}>
            <RefreshCw size={15} />
            Refresh
          </Button>
        </CardHeader>

        <CardContent>
          {error && <Alert variant="destructive">{error}</Alert>}
          {loading && <EmptyState>Loading products...</EmptyState>}
          {!loading && products.length === 0 && !error && <EmptyState>No products are available right now.</EmptyState>}

          <div className="product-grid">
            {products.map((product) => {
              const quantity = cart[product.productId] || 0;

              return (
                <article className="product-row" key={product.productId}>
                  <div className="product-main">
                    <span className="product-icon"><ShoppingCart size={17} /></span>
                    <span>
                      <strong>{product.name}</strong>
                      <small>{product.category} - {product.productId}</small>
                    </span>
                  </div>
                  <strong>{formatMoney(product.price)}</strong>
                  <div className="quantity-stepper" aria-label={`${product.name} quantity`}>
                    <Button
                      type="button"
                      variant="outline"
                      size="icon"
                      onClick={() => onSetQuantity(product.productId, quantity - 1)}
                      disabled={quantity === 0}
                      aria-label={`Remove ${product.name}`}
                    >
                      <Minus size={16} />
                    </Button>
                    <Input
                      type="number"
                      min="0"
                      max="9"
                      value={quantity}
                      onChange={(event) => onSetQuantity(product.productId, event.target.value)}
                      aria-label={`${product.name} quantity`}
                    />
                    <Button
                      type="button"
                      variant="outline"
                      size="icon"
                      onClick={() => onSetQuantity(product.productId, quantity + 1)}
                      disabled={quantity >= 9}
                      aria-label={`Add ${product.name}`}
                    >
                      <Plus size={16} />
                    </Button>
                  </div>
                </article>
              );
            })}
          </div>
        </CardContent>
      </Card>

      <Card className="checkout-panel" aria-labelledby="checkout-title">
        <CardHeader>
          <CardTitle id="checkout-title">Checkout</CardTitle>
          <CardDescription>Orders are saved immediately and appear on your orders page.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="budget-meter">
            <span>
              <small>Remaining budget</small>
              <strong>{formatMoney(budget.remainingBudget)}</strong>
            </span>
            <span>
              <small>Cart total</small>
              <strong className={cn(overBudget && 'danger-text')}>{formatMoney(cartTotal)}</strong>
            </span>
          </div>
          <div className="checkout-summary">
            <Fact label="Items" value={cartCount} />
            <Fact label="Budget after order" value={formatMoney(Math.max(0, Number(budget.remainingBudget || 0) - cartTotal))} />
          </div>
          {overBudget && <Alert variant="destructive">Cart total is higher than your remaining budget.</Alert>}
          {orderError && <Alert variant="destructive">{orderError}</Alert>}
          {orderMessage && <Alert variant="success">{orderMessage}</Alert>}
          <div className="checkout-actions">
            <Button type="button" onClick={onPlaceOrder} disabled={!cartCount || overBudget || isPlacingOrder}>
              <PackageCheck size={16} />
              {isPlacingOrder ? 'Placing order...' : 'Place order'}
            </Button>
            <Button type="button" variant="outline" onClick={onViewOrders}>
              <Ticket size={16} />
              Orders and support
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function OrdersPanel({
  orders,
  ordersLoading,
  ordersError,
  selectedOrderId,
  expandedOrderId,
  orderDetails,
  detailLoadingId,
  onRefresh,
  onCreateOrder,
  onToggleOrder,
}) {
  return (
    <Card aria-labelledby="orders-title">
      <CardHeader className="section-heading">
        <div>
          <CardTitle id="orders-title">Orders</CardTitle>
          <CardDescription>Expand an order to review items and refund status.</CardDescription>
        </div>
        <div className="section-actions">
          <Button type="button" size="sm" onClick={onCreateOrder}>
            <Store size={15} />
            Initiate order
          </Button>
          <Button type="button" variant="outline" size="sm" onClick={onRefresh}>
            <RefreshCw size={15} />
            Refresh
          </Button>
        </div>
      </CardHeader>

      <CardContent>
        {ordersError && <Alert variant="destructive">{ordersError}</Alert>}
        {ordersLoading && <EmptyState>Loading your orders...</EmptyState>}
        {!ordersLoading && orders.length === 0 && !ordersError && (
          <EmptyState>
            <div className="empty-action">
              <span>No orders were found for this customer.</span>
              <Button type="button" size="sm" onClick={onCreateOrder}>
                <Store size={15} />
                Initiate order
              </Button>
            </div>
          </EmptyState>
        )}

        <div className="order-list">
          {orders.map((order) => {
            const isExpanded = expandedOrderId === order.orderId;
            const detail = orderDetails[order.orderId];

            return (
              <article className={cn('record-row', selectedOrderId === order.orderId && 'selected')} key={order.orderId}>
                <button type="button" className="order-summary" onClick={() => onToggleOrder(order.orderId)}>
                  {isExpanded ? <ChevronDown size={18} /> : <ChevronRight size={18} />}
                  <span>
                    <strong>{order.orderId}</strong>
                    <small>{formatDateTime(order.orderTime)}</small>
                  </span>
                  <span>{formatMoney(order.totalAmount)}</span>
                  <Badge variant="outline">{order.itemCount} items</Badge>
                </button>

                {isExpanded && (
                  <div className="order-detail">
                    {detailLoadingId === order.orderId && <p className="muted-line">Loading items...</p>}
                    {Number(detail?.refundAmount || 0) > 0 && (
                      <div className="refund-summary">
                        <span>
                          Refund initiated
                          <small>{formatRefundItems(detail.refundItems)}</small>
                        </span>
                        <strong>{formatMoney(detail.refundAmount)}</strong>
                      </div>
                    )}
                    {detail?.items?.length > 0 && (
                      <div className="items-grid">
                        {detail.items.map((item) => (
                          <div className="item-record" key={item.itemId}>
                            <span>{item.name}</span>
                            <small>{item.itemId}</small>
                            <strong>{formatMoney(item.price)}</strong>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </article>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}

function TicketsPanel({ tickets, loading, error, activeTicketId, isStarting, onCreate, onRefresh, onSelect }) {
  return (
    <Card aria-labelledby="tickets-title">
      <CardHeader className="section-heading">
        <div>
          <CardTitle id="tickets-title">Support</CardTitle>
          <CardDescription>Raise a concern for an order and continue support chats.</CardDescription>
        </div>
        <div className="section-actions">
          <Button type="button" variant="outline" size="icon" onClick={onRefresh} aria-label="Refresh tickets" title="Refresh tickets">
            <RefreshCw size={16} />
          </Button>
          <Button type="button" size="sm" onClick={onCreate} disabled={isStarting}>
            <TicketPlus size={18} />
            {isStarting ? 'Starting...' : 'Raise concern'}
          </Button>
        </div>
      </CardHeader>

      <CardContent>
        {error && <Alert variant="destructive">{error}</Alert>}
        {loading && <EmptyState>Loading tickets...</EmptyState>}
        {!loading && tickets.length === 0 && !error && (
          <EmptyState>
            <div className="empty-action">
              <span>No concerns have been raised yet.</span>
              <Button type="button" size="sm" onClick={onCreate} disabled={isStarting}>
                <TicketPlus size={15} />
                Raise concern
              </Button>
            </div>
          </EmptyState>
        )}
        <div className="ticket-list">
          {tickets.map((ticket) => (
            <button
              type="button"
              className={cn('ticket-row', activeTicketId === ticket.ticketId && 'selected')}
              key={ticket.ticketId}
              onClick={() => onSelect(ticket)}
            >
              <span className="ticket-icon-wrap">
                <Ticket size={16} />
              </span>
              <span>
                <strong>Ticket #{ticket.ticketId}</strong>
                <small>{ticket.issueType || 'New support ticket'} - {formatDateTime(ticket.createdAt)}</small>
              </span>
              <StatusPill label={ticket.status || 'OPEN'} tone={statusTone(ticket.status || 'OPEN')} />
            </button>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

function ChatPanel({ messages, draft, setDraft, onSubmit, connected, waitingForReply, ticketId, isResolved, loading, messagesEndRef }) {
  return (
    <Card className="chat-card" aria-labelledby="support-title">
      <CardHeader className="section-heading">
        <div>
          <CardTitle id="support-title">Support conversation</CardTitle>
          <CardDescription>{ticketId ? `Working in ticket #${ticketId}` : 'Create a ticket or send a message to begin.'}</CardDescription>
        </div>
        <StatusPill label={connected ? 'Live chat ready' : 'Reconnecting'} tone={connected ? 'success' : 'danger'} />
      </CardHeader>

      <CardContent className="chat-content">
        <div className="message-list">
          {loading ? (
            <EmptyState className="empty-chat">Loading ticket conversation...</EmptyState>
          ) : messages.length === 0 ? (
            <EmptyState className="empty-chat">
              Start a ticket, then follow the bot prompts for order, item, and issue details.
            </EmptyState>
          ) : (
            messages.map((message) => (
              <div className={cn('message', message.type)} key={message.id}>
                <div className="message-avatar">{message.type === 'user' ? <UserCircle size={16} /> : <Bot size={16} />}</div>
                <div>
                  <div className="message-bubble">{message.content}</div>
                  <time>{formatTime(message.timestamp)}</time>
                </div>
              </div>
            ))
          )}
          {!loading && waitingForReply && (
            <div className="message bot typing-message" aria-live="polite" aria-label="Support is typing">
              <div className="message-avatar"><Bot size={16} /></div>
              <div className="typing-bubble" aria-hidden="true">
                <span />
                <span />
                <span />
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        <form className="composer" onSubmit={onSubmit}>
          <Input
            type="text"
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder={isResolved ? 'This ticket is resolved' : connected ? 'Type your message' : 'Reconnecting to chat...'}
            aria-label="Chat message"
            disabled={isResolved}
          />
          <Button type="submit" disabled={!draft.trim() || isResolved}>
            <Send size={16} />
            {isResolved ? 'Resolved' : 'Send'}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

function AdminApp() {
  const savedAdminSession = readSavedAdminSession();
  const [adminId, setAdminId] = useState(savedAdminSession?.admin?.adminId || 'admin-1');
  const [password, setPassword] = useState('');
  const [admin, setAdmin] = useState(savedAdminSession?.admin || null);
  const [adminToken, setAdminToken] = useState(savedAdminSession?.token || '');
  const [loginError, setLoginError] = useState('');
  const [isLoggingIn, setIsLoggingIn] = useState(false);

  const [tickets, setTickets] = useState([]);
  const [ticketsLoading, setTicketsLoading] = useState(false);
  const [ticketsError, setTicketsError] = useState('');
  const [selectedTicketId, setSelectedTicketId] = useState(null);
  const [ticketDetail, setTicketDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [replyDraft, setReplyDraft] = useState('');
  const [refundDraft, setRefundDraft] = useState('');
  const [actionError, setActionError] = useState('');
  const [actionMessage, setActionMessage] = useState('');
  const [isSendingReply, setIsSendingReply] = useState(false);
  const [isRefunding, setIsRefunding] = useState(false);
  const [isResolving, setIsResolving] = useState(false);
  const adminClientRef = useRef(null);
  const selectedTicketRef = useRef(selectedTicketId);

  useEffect(() => {
    if (!admin || !adminToken) return;
    fetchEscalatedTickets(adminToken);
  }, [admin, adminToken]);

  useEffect(() => {
    selectedTicketRef.current = selectedTicketId;
  }, [selectedTicketId]);

  useEffect(() => {
    if (!admin || !adminToken) return undefined;
    connectAdminWebSocket();

    return () => {
      adminClientRef.current?.deactivate();
      adminClientRef.current = null;
    };
  }, [admin, adminToken]);

  async function loginAdmin(event) {
    event.preventDefault();
    setLoginError('');
    setIsLoggingIn(true);

    try {
      const response = await fetch(`${API_BASE_URL}/api/admin/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          adminId: adminId.trim(),
          password,
        }),
      });

      if (response.status === 401 || response.status === 403) {
        setLoginError('Admin ID or password is incorrect.');
        return;
      }

      if (!response.ok) {
        setLoginError('Admin login failed. Check that the backend is running.');
        return;
      }

      const data = await response.json();
      const nextAdmin = {
        adminId: data.adminId,
        fullName: data.fullName,
      };

      sessionStorage.setItem('support-admin-session', JSON.stringify({ admin: nextAdmin, token: data.accessToken }));
      setAdmin(nextAdmin);
      setAdminToken(data.accessToken);
      setPassword('');
    } catch (error) {
      setLoginError('Could not reach the admin API at http://localhost:8080.');
    } finally {
      setIsLoggingIn(false);
    }
  }

  function logoutAdmin() {
    sessionStorage.removeItem('support-admin-session');
    adminClientRef.current?.deactivate();
    adminClientRef.current = null;
    setAdmin(null);
    setAdminToken('');
    setTickets([]);
    setSelectedTicketId(null);
    setTicketDetail(null);
    setReplyDraft('');
    setRefundDraft('');
    setActionError('');
    setActionMessage('');
  }

  async function fetchEscalatedTickets(accessToken = adminToken) {
    setTicketsLoading(true);
    setTicketsError('');
    setActionMessage('');

    try {
      const response = await adminFetch('/api/admin/tickets/escalated', accessToken);

      if (response.status === 401 || response.status === 403) {
        expireAdminSession();
        return;
      }

      if (!response.ok) {
        setTicketsError('Open escalated tickets could not be loaded.');
        return;
      }

      const data = await response.json();
      const nextTickets = Array.isArray(data) ? data : [];
      setTickets(nextTickets);

      if (selectedTicketId && !nextTickets.some((ticket) => getTicketId(ticket) === selectedTicketId)) {
        setSelectedTicketId(null);
        setTicketDetail(null);
      }
    } catch (error) {
      setTicketsError('Could not reach the escalated tickets API.');
    } finally {
      setTicketsLoading(false);
    }
  }

  async function openTicket(ticket) {
    const nextTicketId = getTicketId(ticket);
    if (!nextTicketId) return;

    setSelectedTicketId(nextTicketId);
    setDetailLoading(true);
    setActionError('');
    setActionMessage('');

    try {
      const response = await adminFetch(`/api/admin/tickets/${encodeURIComponent(nextTicketId)}`, adminToken);

      if (response.status === 401 || response.status === 403) {
        expireAdminSession();
        return;
      }

      if (!response.ok) {
        setActionError(`Ticket ${nextTicketId} could not be loaded.`);
        return;
      }

      setTicketDetail(await response.json());
    } catch (error) {
      setActionError(`Ticket ${nextTicketId} could not be loaded.`);
    } finally {
      setDetailLoading(false);
    }
  }

  function sendAdminReply(event) {
    event.preventDefault();
    const message = replyDraft.trim();
    if (!message || !selectedTicketId) return;

    setIsSendingReply(true);
    setActionError('');
    setActionMessage('');

    if (!adminClientRef.current?.connected) {
      connectAdminWebSocket();
      setIsSendingReply(false);
      setActionError('Chat is reconnecting. Please send that reply again in a moment.');
      return;
    }

    adminClientRef.current.publish({
      destination: SEND_ADMIN_REPLY_ENDPOINT,
      body: JSON.stringify({
        ticketId: selectedTicketId,
        message,
      }),
    });
    setReplyDraft('');
    setActionMessage('Reply sent.');
    setIsSendingReply(false);
  }

  async function initiateRefund(event) {
    event.preventDefault();
    if (!selectedTicketId) return;

    const amount = Number(refundDraft);
    if (!Number.isFinite(amount) || amount <= 0) {
      setActionError('Enter a refund amount greater than 0.');
      return;
    }

    setIsRefunding(true);
    setActionError('');
    setActionMessage('');

    try {
      const response = await adminFetch(`/api/admin/tickets/${encodeURIComponent(selectedTicketId)}/refund`, adminToken, {
        method: 'POST',
        body: JSON.stringify({ amount }),
      });

      if (response.status === 401 || response.status === 403) {
        expireAdminSession();
        return;
      }

      if (!response.ok) {
        setActionError('Refund could not be initiated.');
        return;
      }

      setRefundDraft('');
      setActionMessage(`Refund of ${formatMoney(amount)} initiated.`);
      setTicketDetail((previous) => ({
        ...(previous || {}),
        ticketId: selectedTicketId,
        state: 'RESOLVED',
        status: 'RESOLVED',
        refundAmount: amount,
      }));
      await refreshSelectedTicket();
      await fetchEscalatedTickets();
    } catch (error) {
      setActionError('Refund could not be initiated.');
    } finally {
      setIsRefunding(false);
    }
  }

  async function resolveTicket() {
    if (!selectedTicketId) return;

    setIsResolving(true);
    setActionError('');
    setActionMessage('');

    try {
      const response = await adminFetch(`/api/admin/tickets/${encodeURIComponent(selectedTicketId)}/resolve`, adminToken, {
        method: 'POST',
      });

      if (response.status === 401 || response.status === 403) {
        expireAdminSession();
        return;
      }

      if (!response.ok) {
        setActionError('Ticket could not be resolved.');
        return;
      }

      setActionMessage('Ticket resolved without refund.');
      setTicketDetail((previous) => ({
        ...(previous || {}),
        ticketId: selectedTicketId,
        state: 'RESOLVED',
        status: 'RESOLVED',
        refundAmount: 0,
      }));
      await refreshSelectedTicket();
      await fetchEscalatedTickets();
    } catch (error) {
      setActionError('Ticket could not be resolved.');
    } finally {
      setIsResolving(false);
    }
  }

  async function refreshSelectedTicket() {
    if (!selectedTicketId) return;
    await openTicket({ ticketId: selectedTicketId });
  }

  function expireAdminSession() {
    sessionStorage.removeItem('support-admin-session');
    adminClientRef.current?.deactivate();
    adminClientRef.current = null;
    setAdmin(null);
    setAdminToken('');
    setLoginError('Your admin session expired. Please log in again.');
  }

  function connectAdminWebSocket() {
    if (adminClientRef.current?.active) return;

    const stompClient = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: {
        Authorization: `Bearer ${adminToken}`,
      },
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        stompClient.subscribe(ADMIN_TICKET_TOPIC, (frame) => {
          try {
            const response = JSON.parse(frame.body);
            receiveAdminMessage(response);
          } catch (error) {
            console.error('Invalid admin ticket update', error);
          }
        });
      },
    });

    adminClientRef.current = stompClient;
    stompClient.activate();
  }

  function receiveAdminMessage(response) {
    if (!['USER', 'ADMIN'].includes(response.sender) || !response.message?.trim()) return;

    const activeTicketId = selectedTicketRef.current;
    if (!activeTicketId || response.ticketId !== activeTicketId) {
      fetchEscalatedTickets();
      return;
    }

    setTicketDetail((previous) => {
      if (!previous || previous.ticketId !== response.ticketId) return previous;
      const nextMessage = {
        id: `${response.timestamp || Date.now()}-${response.sender.toLowerCase()}-live`,
        sender: response.sender,
        message: response.message,
        timestamp: response.timestamp || new Date().toISOString(),
      };
      return {
        ...previous,
        messages: [...getTicketMessages(previous), nextMessage],
      };
    });
  }

  if (!admin) {
    return (
      <main className="login-page">
        <Card className="login-panel" aria-labelledby="admin-login-title">
          <CardHeader>
            <Badge variant="outline">Support admin</Badge>
            <CardTitle id="admin-login-title">Admin login</CardTitle>
            <CardDescription>Sign in to view and handle escalated support tickets.</CardDescription>
          </CardHeader>

          <CardContent>
            <form className="stack-form" onSubmit={loginAdmin}>
              <div className="field">
                <Label htmlFor="adminId">Admin ID</Label>
                <Input
                  id="adminId"
                  type="text"
                  value={adminId}
                  onChange={(event) => setAdminId(event.target.value)}
                  placeholder="admin-1"
                  autoComplete="username"
                />
              </div>

              <div className="field">
                <Label htmlFor="adminPassword">Password</Label>
                <Input
                  id="adminPassword"
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="admin123"
                  autoComplete="current-password"
                />
                <p className="hint">Demo admin: admin-1 / admin123</p>
              </div>

              {loginError && <Alert variant="destructive">{loginError}</Alert>}
              <Button type="submit" disabled={isLoggingIn}>
                <ShieldCheck size={16} />
                {isLoggingIn ? 'Logging in...' : 'Log in as admin'}
              </Button>
            </form>
          </CardContent>
        </Card>
      </main>
    );
  }

  const messages = getTicketMessages(ticketDetail);
  const selectedItems = getTicketItems(ticketDetail);

  return (
    <div className="app-shell">
      <header className="top-bar">
        <div className="title-group">
          <Badge variant="outline">Support Admin</Badge>
          <h1>Escalated tickets</h1>
        </div>
        <div className="header-meta">
          <StatusPill label={admin.fullName || admin.adminId} tone="info" />
          <Button type="button" variant="outline" onClick={() => { window.history.pushState({}, '', '/'); window.location.reload(); }}>
            Customer app
          </Button>
          <Button type="button" variant="outline" onClick={logoutAdmin}>
            <LogOut size={16} />
            Logout
          </Button>
        </div>
      </header>

      <main className="admin-page">
        <Card aria-labelledby="ticket-list-title">
          <CardHeader className="section-heading">
            <div>
              <CardTitle id="ticket-list-title">Open escalations</CardTitle>
              <CardDescription>Tickets waiting for a support admin reply or resolution.</CardDescription>
            </div>
            <Button type="button" variant="outline" size="sm" onClick={() => fetchEscalatedTickets()}>
              <RefreshCw size={15} />
              Refresh
            </Button>
          </CardHeader>

          <CardContent>
            {ticketsError && <Alert variant="destructive">{ticketsError}</Alert>}
            {ticketsLoading && <EmptyState>Loading escalated tickets...</EmptyState>}
            {!ticketsLoading && tickets.length === 0 && !ticketsError && <EmptyState>No open escalated tickets.</EmptyState>}

            <div className="ticket-list">
              {tickets.map((ticket) => {
                const id = getTicketId(ticket);
                return (
                  <button
                    type="button"
                    className={cn('ticket-row', selectedTicketId === id && 'selected')}
                    key={id || JSON.stringify(ticket)}
                    onClick={() => openTicket(ticket)}
                  >
                    <span className="ticket-icon-wrap">
                      <ClipboardList size={16} />
                    </span>
                    <span>
                      <strong>Ticket #{id || 'Unknown'}</strong>
                      <small>{ticket.userId || ticket.customerUserId || 'Unknown user'}</small>
                    </span>
                    <StatusPill label={ticket.status || 'ESCALATED'} tone={statusTone(ticket.status || 'ESCALATED')} />
                  </button>
                );
              })}
            </div>
          </CardContent>
        </Card>

        <Card aria-labelledby="ticket-detail-title">
          <CardHeader className="section-heading">
            <div>
              <CardTitle id="ticket-detail-title">{selectedTicketId ? `Ticket #${selectedTicketId}` : 'Ticket detail'}</CardTitle>
              <CardDescription>Reply to the customer or resolve the ticket when the issue is complete.</CardDescription>
            </div>
            {selectedTicketId && (
              <Button type="button" variant="outline" size="sm" onClick={refreshSelectedTicket}>
                <RefreshCw size={15} />
                Refresh detail
              </Button>
            )}
          </CardHeader>

          <CardContent>
            {actionError && <Alert variant="destructive">{actionError}</Alert>}
            {actionMessage && <Alert variant="success">{actionMessage}</Alert>}
            {detailLoading && <EmptyState>Loading ticket detail...</EmptyState>}
            {!selectedTicketId && !detailLoading && <EmptyState>Select an escalated ticket to view its conversation.</EmptyState>}

            {selectedTicketId && ticketDetail && !detailLoading && (
              <div className="admin-detail-grid">
                <div className="ticket-facts">
                  <Fact label="Customer" value={ticketDetail.userId || ticketDetail.customerUserId || 'Unknown'} />
                  <Fact label="Order" value={ticketDetail.orderId || 'Not available'} />
                  <Fact label="State" value={ticketDetail.state || 'ESCALATED'} />
                  <Fact label="Status" value={ticketDetail.status || 'ESCALATED'} />
                  <Fact label="Refund" value={formatMoney(ticketDetail.refundAmount || 0)} />
                </div>

                {selectedItems.length > 0 && (
                  <div className="admin-section">
                    <h3>Selected items</h3>
                    <div className="items-grid">
                      {selectedItems.map((item, index) => (
                        <div className="item-record" key={item.itemId || item.name || index}>
                          <span>{item.name || item.itemName || `Item ${index + 1}`}</span>
                          <small>{item.itemId || item.id || 'No item ID'}</small>
                          {'price' in item && <strong>{formatMoney(item.price)}</strong>}
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                <div className="admin-section">
                  <h3>Conversation</h3>
                  {messages.length === 0 ? (
                    <EmptyState>No conversation messages were returned for this ticket.</EmptyState>
                  ) : (
                    <div className="message-list admin-messages">
                      {messages.map((message, index) => (
                        <div className={cn('message', messageSenderClass(message))} key={message.id || message.messageId || index}>
                          <div className="message-avatar">{messageSenderClass(message) === 'user' ? <UserCircle size={16} /> : <Bot size={16} />}</div>
                          <div>
                            <div className="message-bubble">{message.message || message.content || ''}</div>
                            <time>{message.sender || message.senderType || 'SYSTEM'} {message.timestamp ? `- ${formatTime(message.timestamp)}` : ''}</time>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <form className="stack-form" onSubmit={sendAdminReply}>
                  <div className="field">
                    <Label htmlFor="adminReply">Reply as admin</Label>
                    <Textarea
                      id="adminReply"
                      value={replyDraft}
                      onChange={(event) => setReplyDraft(event.target.value)}
                      placeholder="Type a customer-facing update"
                      rows="4"
                    />
                  </div>
                  <div className="admin-actions">
                    <Button type="submit" disabled={!replyDraft.trim() || isSendingReply}>
                      <Send size={16} />
                      {isSendingReply ? 'Sending...' : 'Send reply'}
                    </Button>
                  </div>
                </form>

                <form className="stack-form" onSubmit={initiateRefund}>
                  <div className="field">
                    <Label htmlFor="refundAmount">Refund amount</Label>
                    <Input
                      id="refundAmount"
                      type="number"
                      min="1"
                      step="1"
                      value={refundDraft}
                      onChange={(event) => setRefundDraft(event.target.value)}
                      placeholder="1000"
                    />
                    <p className="hint">Use this when the agent decides to close the ticket with a refund.</p>
                  </div>
                  <div className="admin-actions">
                    <Button type="submit" disabled={!refundDraft || isRefunding || ticketDetail.status === 'RESOLVED'}>
                      {isRefunding ? 'Initiating...' : 'Initiate refund'}
                    </Button>
                    <Button
                      type="button"
                      variant="destructive"
                      onClick={resolveTicket}
                      disabled={isResolving || ticketDetail.status === 'RESOLVED'}
                    >
                      {isResolving ? 'Resolving...' : 'Resolve without refund'}
                    </Button>
                  </div>
                </form>
              </div>
            )}
          </CardContent>
        </Card>
      </main>
    </div>
  );
}

function authorizedFetch(path, accessToken, options = {}) {
  return fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {}),
      Authorization: `Bearer ${accessToken}`,
    },
  });
}

function adminFetch(path, accessToken, options = {}) {
  return fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {}),
      Authorization: `Bearer ${accessToken}`,
    },
  });
}

function normalizeCustomer(data) {
  return {
    userId: data.userId,
    fullName: data.fullName,
    email: data.email,
    shoppingBudget: Number(data.shoppingBudget || 0),
    spentAmount: Number(data.spentAmount || 0),
    remainingBudget: Number(data.remainingBudget || 0),
  };
}

function saveCustomerSession(user, token) {
  sessionStorage.setItem('support-chat-session', JSON.stringify({ user, token }));
}

function emptyBudget() {
  return {
    shoppingBudget: 0,
    spentAmount: 0,
    remainingBudget: 0,
  };
}

function userBudget(user) {
  return {
    shoppingBudget: Number(user?.shoppingBudget || 0),
    spentAmount: Number(user?.spentAmount || 0),
    remainingBudget: Number(user?.remainingBudget || 0),
  };
}

function calculateCartTotal(products, cart) {
  return products.reduce((total, product) => {
    const quantity = cart[product.productId] || 0;
    return total + Number(product.price || 0) * quantity;
  }, 0);
}

function readSavedSession() {
  try {
    const value = sessionStorage.getItem('support-chat-session');
    return value ? JSON.parse(value) : null;
  } catch (error) {
    return null;
  }
}

function readSavedAdminSession() {
  try {
    const value = sessionStorage.getItem('support-admin-session');
    return value ? JSON.parse(value) : null;
  } catch (error) {
    return null;
  }
}

function Stat({ icon: Icon, label, value, detail }) {
  return (
    <div className="stat">
      <span className="stat-icon"><Icon size={17} /></span>
      <span>
        <small>{label}</small>
        <strong>{value}</strong>
        {detail && <em>{detail}</em>}
      </span>
    </div>
  );
}

function Fact({ label, value }) {
  return (
    <div>
      <span className="label">{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function EmptyState({ className, children }) {
  return <div className={cn('empty-panel', className)}>{children}</div>;
}

function getTicketId(ticket) {
  return ticket?.ticketId ?? ticket?.id ?? ticket?.ticket?.ticketId ?? null;
}

function getTicketMessages(ticketDetail) {
  if (!ticketDetail) return [];
  return ticketDetail.messages || ticketDetail.conversationMessages || ticketDetail.chatMessages || ticketDetail.conversation || [];
}

function getTicketItems(ticketDetail) {
  if (!ticketDetail) return [];
  return ticketDetail.selectedItems || ticketDetail.items || ticketDetail.orderItems || [];
}

function mapTicketMessages(ticketMessages = []) {
  return ticketMessages.map((message, index) => ({
    id: `${message.id || message.messageId || index}-${message.sender || 'message'}`,
    type: messageSenderClass(message),
    content: message.message || message.content || '',
    timestamp: message.timestamp || new Date().toISOString(),
  }));
}

function messageSenderClass(message) {
  const sender = String(message.sender || message.senderType || '').toLowerCase();
  if (sender.includes('customer') || sender.includes('user')) return 'user';
  return 'bot';
}

function StatusPill({ label, tone }) {
  return <Badge variant={statusVariant(tone)}>{label}</Badge>;
}

function statusVariant(tone) {
  if (tone === 'success') return 'success';
  if (tone === 'warning') return 'warning';
  if (tone === 'danger') return 'danger';
  if (tone === 'info') return 'info';
  return 'secondary';
}

function statusTone(status) {
  if (status === 'RESOLVED') return 'success';
  if (status === 'ESCALATED') return 'warning';
  if (status === 'OPEN') return 'info';
  return 'neutral';
}

function formatMoney(amount) {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(amount || 0);
}

function formatRefundItems(items = []) {
  if (!items.length) return 'Refunded items not available';

  const itemNumbers = items
    .map((item) => item.itemNumber)
    .filter((itemNumber) => itemNumber !== null && itemNumber !== undefined)
    .sort((first, second) => first - second);
  const itemLabel = itemNumbers.length
    ? `Item${itemNumbers.length > 1 ? 's' : ''} ${formatNumberList(itemNumbers)}`
    : 'Selected items';
  const names = items.map((item) => item.name).filter(Boolean);

  return names.length ? `${itemLabel}: ${names.join(', ')}` : itemLabel;
}

function formatNumberList(values) {
  if (values.length <= 1) return values.join('');
  return `${values.slice(0, -1).join(', ')} & ${values[values.length - 1]}`;
}

function formatDateTime(value) {
  if (!value) return 'Unknown time';
  return new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

function formatTime(value) {
  return new Intl.DateTimeFormat('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

export default App;
