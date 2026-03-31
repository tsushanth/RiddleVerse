// utils/usageTracker.js

import nodemailer from 'nodemailer';

/**
 * Simple AI Usage Tracking System with Email Alerts
 * Tracks costs and sends email alerts when thresholds are reached
 */

// Model pricing per 1K tokens (input/output) - Update these with actual pricing
const MODEL_PRICING = {
  'gpt-3.5-turbo': { input: 0.0015, output: 0.002 },
  'deepseek': { input: 0.0014, output: 0.0028 }, // Estimated
  'claude-3-sonnet': { input: 0.003, output: 0.015 },
  'gemini-pro': { input: 0.00025, output: 0.0005 }
};

// Email configuration (using same settings as server.js)
const transporter = nodemailer.createTransport({
    service: "gmail",
    auth: {
        user: "puzzleverseai@gmail.com",
        pass: "duoo ukes bjkx nanj"
    }
});

// Usage thresholds for email alerts
const USAGE_THRESHOLDS = {
  DAILY_COST: 10.00,      // Alert when daily cost exceeds $10
  WEEKLY_COST: 50.00,     // Alert when weekly cost exceeds $50
  MONTHLY_COST: 200.00,   // Alert when monthly cost exceeds $200
  SINGLE_REQUEST: 1.00,   // Alert when single request costs more than $1
  HOURLY_REQUESTS: 100    // Alert when hourly requests exceed 100
};

class SimpleUsageTracker {
  constructor() {
    this.dailyUsage = new Map();
    this.weeklyUsage = new Map();
    this.monthlyUsage = new Map();
    this.hourlyUsage = new Map();
    this.hourlyRequests = new Map();
    this.lastEmailAlert = new Map(); // Track when we last sent alerts to avoid spam
    this.costBreakdown = new Map(); // Track what's causing the most cost
  }

  /**
   * Get current date key for tracking
   */
  getDateKey(type = 'daily') {
    const now = new Date();
    switch (type) {
      case 'daily':
        return now.toISOString().split('T')[0]; // YYYY-MM-DD
      case 'weekly':
        const startOfWeek = new Date(now.setDate(now.getDate() - now.getDay()));
        return startOfWeek.toISOString().split('T')[0];
      case 'monthly':
        return `${now.getFullYear()}-${(now.getMonth() + 1).toString().padStart(2, '0')}`;
      case 'hourly':
        return `${now.toISOString().split('T')[0]}-${now.getHours()}`;
      default:
        return now.toISOString().split('T')[0];
    }
  }

  /**
   * Estimate token count from text (rough approximation)
   */
  estimateTokens(text) {
    if (!text || typeof text !== 'string') return 0;
    return Math.ceil(text.length / 4);
  }

  /**
   * Calculate cost based on model, tokens, and type
   */
  calculateCost(modelName, inputTokens, outputTokens) {
    const pricing = MODEL_PRICING[modelName];
    if (!pricing) return 0;
    
    const inputCost = (inputTokens / 1000) * pricing.input;
    const outputCost = (outputTokens / 1000) * pricing.output;
    return inputCost + outputCost;
  }

  /**
   * Track an AI request and check thresholds
   */
  async trackRequest({
    modelName,
    category = 'other',
    prompt,
    response,
    success = true,
    puzzleType = null,
    difficulty = null
  }) {
    const inputTokens = this.estimateTokens(prompt);
    const outputTokens = this.estimateTokens(response);
    const cost = this.calculateCost(modelName, inputTokens, outputTokens);

    const dailyKey = this.getDateKey('daily');
    const weeklyKey = this.getDateKey('weekly');
    const monthlyKey = this.getDateKey('monthly');
    const hourlyKey = this.getDateKey('hourly');

    // Update usage tracking
    this.updateUsage('daily', dailyKey, cost, 1);
    this.updateUsage('weekly', weeklyKey, cost, 1);
    this.updateUsage('monthly', monthlyKey, cost, 1);
    this.updateUsage('hourly', hourlyKey, cost, 1);

    // Track cost breakdown
    const activityKey = `${category}${puzzleType ? `/${puzzleType}` : ''}${difficulty ? `/${difficulty}` : ''}`;
    this.updateCostBreakdown(activityKey, cost, modelName);

    // Check thresholds and send alerts if needed
    await this.checkThresholds(cost, activityKey, modelName, {
      dailyKey,
      weeklyKey,
      monthlyKey,
      hourlyKey,
      category,
      puzzleType,
      difficulty
    });

    return { cost, tokens: inputTokens + outputTokens, inputTokens, outputTokens };
  }

  /**
   * Update usage maps
   */
  updateUsage(type, key, cost, requests) {
    const usageMap = this[`${type}Usage`];
    if (!usageMap.has(key)) {
      usageMap.set(key, { cost: 0, requests: 0 });
    }
    const current = usageMap.get(key);
    current.cost += cost;
    current.requests += requests;
  }

  /**
   * Update cost breakdown tracking
   */
  updateCostBreakdown(activityKey, cost, modelName) {
    if (!this.costBreakdown.has(activityKey)) {
      this.costBreakdown.set(activityKey, { 
        totalCost: 0, 
        requests: 0, 
        models: new Map() 
      });
    }
    
    const breakdown = this.costBreakdown.get(activityKey);
    breakdown.totalCost += cost;
    breakdown.requests += 1;
    
    if (!breakdown.models.has(modelName)) {
      breakdown.models.set(modelName, { cost: 0, requests: 0 });
    }
    const modelStats = breakdown.models.get(modelName);
    modelStats.cost += cost;
    modelStats.requests += 1;
  }

  /**
   * Check thresholds and send email alerts
   */
  async checkThresholds(requestCost, activityKey, modelName, keys) {
    const alerts = [];

    // Check single request cost
    if (requestCost > USAGE_THRESHOLDS.SINGLE_REQUEST) {
      alerts.push({
        type: 'HIGH_SINGLE_COST',
        amount: requestCost,
        threshold: USAGE_THRESHOLDS.SINGLE_REQUEST,
        activity: activityKey,
        model: modelName
      });
    }

    // Check daily cost
    const dailyCost = this.dailyUsage.get(keys.dailyKey)?.cost || 0;
    if (dailyCost > USAGE_THRESHOLDS.DAILY_COST) {
      if (!this.lastEmailAlert.has(`daily-${keys.dailyKey}`)) {
        alerts.push({
          type: 'DAILY_THRESHOLD',
          amount: dailyCost,
          threshold: USAGE_THRESHOLDS.DAILY_COST,
          period: keys.dailyKey
        });
        this.lastEmailAlert.set(`daily-${keys.dailyKey}`, Date.now());
      }
    }

    // Check weekly cost
    const weeklyCost = this.weeklyUsage.get(keys.weeklyKey)?.cost || 0;
    if (weeklyCost > USAGE_THRESHOLDS.WEEKLY_COST) {
      if (!this.lastEmailAlert.has(`weekly-${keys.weeklyKey}`)) {
        alerts.push({
          type: 'WEEKLY_THRESHOLD',
          amount: weeklyCost,
          threshold: USAGE_THRESHOLDS.WEEKLY_COST,
          period: keys.weeklyKey
        });
        this.lastEmailAlert.set(`weekly-${keys.weeklyKey}`, Date.now());
      }
    }

    // Check monthly cost
    const monthlyCost = this.monthlyUsage.get(keys.monthlyKey)?.cost || 0;
    if (monthlyCost > USAGE_THRESHOLDS.MONTHLY_COST) {
      if (!this.lastEmailAlert.has(`monthly-${keys.monthlyKey}`)) {
        alerts.push({
          type: 'MONTHLY_THRESHOLD',
          amount: monthlyCost,
          threshold: USAGE_THRESHOLDS.MONTHLY_COST,
          period: keys.monthlyKey
        });
        this.lastEmailAlert.set(`monthly-${keys.monthlyKey}`, Date.now());
      }
    }

    // Check hourly request rate
    const hourlyRequests = this.hourlyUsage.get(keys.hourlyKey)?.requests || 0; // ✅ Fixed: Changed from hourlyRequests to hourlyUsage
    if (hourlyRequests > USAGE_THRESHOLDS.HOURLY_REQUESTS) {
      if (!this.lastEmailAlert.has(`hourly-${keys.hourlyKey}`)) {
        alerts.push({
          type: 'HOURLY_REQUESTS',
          amount: hourlyRequests,
          threshold: USAGE_THRESHOLDS.HOURLY_REQUESTS,
          period: keys.hourlyKey
        });
        this.lastEmailAlert.set(`hourly-${keys.hourlyKey}`, Date.now());
      }
    }

    // Send email if there are alerts
    if (alerts.length > 0) {
      await this.sendEmailAlert(alerts, keys);
    }
  }

  /**
   * Send email alert
   */
  async sendEmailAlert(alerts, context) {
    try {
      const topActivities = this.getTopCostDrivers(5);
      
      const subject = `🚨 AI Usage Alert - ${alerts.map(a => a.type).join(', ')}`;
      
      const htmlContent = this.generateEmailHTML(alerts, topActivities, context);
      
      const mailOptions = {
        from: '"AI Usage Monitor" <puzzleverseai@gmail.com>',
        to: 'puzzleverseai@gmail.com',
        subject: subject,
        html: htmlContent
      };

      await transporter.sendMail(mailOptions);
      console.log('📧 Usage alert email sent successfully');
      
    } catch (error) {
      console.error('❌ Failed to send usage alert email:', error);
    }
  }

  /**
   * Generate HTML email content
   */
  generateEmailHTML(alerts, topActivities, context) {
    const alertsHTML = alerts.map(alert => {
      const emoji = {
        'HIGH_SINGLE_COST': '💰',
        'DAILY_THRESHOLD': '📅',
        'WEEKLY_THRESHOLD': '📊',
        'MONTHLY_THRESHOLD': '📈',
        'HOURLY_REQUESTS': '⚡'
      }[alert.type] || '⚠️';

      const formatAmount = alert.type === 'HOURLY_REQUESTS' 
        ? `${alert.amount} requests` 
        : `${alert.amount.toFixed(2)}`;
      
      const formatThreshold = alert.type === 'HOURLY_REQUESTS'
        ? `${alert.threshold} requests`
        : `${alert.threshold.toFixed(2)}`;

      return `
        <div style="background: #f8f9fa; padding: 15px; margin: 10px 0; border-left: 4px solid #dc3545; border-radius: 4px;">
          <h3 style="margin: 0; color: #dc3545;">${emoji} ${alert.type.replace('_', ' ')}</h3>
          <p><strong>Current:</strong> ${formatAmount}</p>
          <p><strong>Threshold:</strong> ${formatThreshold}</p>
          ${alert.period ? `<p><strong>Period:</strong> ${alert.period}</p>` : ''}
          ${alert.activity ? `<p><strong>Activity:</strong> ${alert.activity}</p>` : ''}
          ${alert.model ? `<p><strong>Model:</strong> ${alert.model}</p>` : ''}
        </div>
      `;
    }).join('');

    const topActivitiesHTML = topActivities.map((activity, index) => `
      <tr style="background: ${index % 2 === 0 ? '#f8f9fa' : 'white'};">
        <td style="padding: 8px; border: 1px solid #dee2e6;">${activity.activity}</td>
        <td style="padding: 8px; border: 1px solid #dee2e6; text-align: right;">${activity.cost.toFixed(2)}</td>
        <td style="padding: 8px; border: 1px solid #dee2e6; text-align: center;">${activity.requests}</td>
        <td style="padding: 8px; border: 1px solid #dee2e6; text-align: right;">${activity.avgCost.toFixed(3)}</td>
      </tr>
    `).join('');

    return `
      <html>
        <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
          <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
            <h1 style="color: #dc3545; border-bottom: 2px solid #dc3545; padding-bottom: 10px;">
              🚨 AI Usage Alert
            </h1>
            
            <p>The following usage thresholds have been exceeded:</p>
            
            ${alertsHTML}
            
            <h2 style="color: #495057; margin-top: 30px;">Top Cost Drivers</h2>
            <table style="width: 100%; border-collapse: collapse; margin: 15px 0;">
              <thead>
                <tr style="background: #495057; color: white;">
                  <th style="padding: 10px; border: 1px solid #dee2e6; text-align: left;">Activity</th>
                  <th style="padding: 10px; border: 1px solid #dee2e6; text-align: right;">Total Cost</th>
                  <th style="padding: 10px; border: 1px solid #dee2e6; text-align: center;">Requests</th>
                  <th style="padding: 10px; border: 1px solid #dee2e6; text-align: right;">Avg Cost</th>
                </tr>
              </thead>
              <tbody>
                ${topActivitiesHTML}
              </tbody>
            </table>
            
            <div style="background: #e9ecef; padding: 15px; border-radius: 4px; margin-top: 20px;">
              <h3 style="margin-top: 0; color: #495057;">📊 Quick Stats</h3>
              <p><strong>Daily Cost:</strong> ${(this.dailyUsage.get(context.dailyKey)?.cost || 0).toFixed(2)}</p>
              <p><strong>Weekly Cost:</strong> ${(this.weeklyUsage.get(context.weeklyKey)?.cost || 0).toFixed(2)}</p>
              <p><strong>Monthly Cost:</strong> ${(this.monthlyUsage.get(context.monthlyKey)?.cost || 0).toFixed(2)}</p>
              <p><strong>Alert Time:</strong> ${new Date().toLocaleString()}</p>
            </div>
            
            <p style="margin-top: 30px; font-size: 12px; color: #6c757d;">
              This is an automated alert from your AI Usage Monitoring System.
            </p>
          </div>
        </body>
      </html>
    `;
  }

  /**
   * Get top cost drivers
   */
  getTopCostDrivers(limit = 5) {
    return Array.from(this.costBreakdown.entries())
      .map(([activity, data]) => ({
        activity,
        cost: data.totalCost,
        requests: data.requests,
        avgCost: data.totalCost / data.requests
      }))
      .sort((a, b) => b.cost - a.cost)
      .slice(0, limit);
  }

  /**
   * Get current usage stats
   */
  getStats() {
    const dailyKey = this.getDateKey('daily');
    const weeklyKey = this.getDateKey('weekly');
    const monthlyKey = this.getDateKey('monthly');

    return {
      daily: this.dailyUsage.get(dailyKey) || { cost: 0, requests: 0 },
      weekly: this.weeklyUsage.get(weeklyKey) || { cost: 0, requests: 0 },
      monthly: this.monthlyUsage.get(monthlyKey) || { cost: 0, requests: 0 },
      topActivities: this.getTopCostDrivers(5),
      thresholds: USAGE_THRESHOLDS
    };
  }

  /**
   * Reset usage data (for testing or new periods)
   */
  reset() {
    this.dailyUsage.clear();
    this.weeklyUsage.clear();
    this.monthlyUsage.clear();
    this.hourlyUsage.clear();
    this.hourlyRequests.clear();
    this.lastEmailAlert.clear();
    this.costBreakdown.clear();
    console.log('🔄 Usage tracker reset');
  }
}

// Create singleton instance
const usageTracker = new SimpleUsageTracker();

// Usage categories for easier tracking
export const USAGE_CATEGORIES = {
  PUZZLE_GENERATION: 'puzzle_generation',
  PUZZLE_VALIDATION: 'puzzle_validation',
  CUSTOM_PUZZLE_GENERATION: 'custom_puzzle_generation', 
  TTS_GENERATION: 'tts_generation',
  ERROR_RETRY: 'error_retry',
  HEALTH_CHECK: 'health_check',
  SUGGESTION_GENERATION: 'suggestion_generation',
  OTHER: 'other'
};

export { usageTracker };
export default usageTracker;