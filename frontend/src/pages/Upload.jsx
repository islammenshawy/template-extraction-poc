import React, { useState } from 'react';
import { messagesApi } from '../services/api';
import './Upload.css';

function Upload() {
  const [uploadMode, setUploadMode] = useState('single');
  const [formData, setFormData] = useState({
    messageType: 'MT700',
    rawContent: '',
    senderId: '',
    receiverId: '',
    notes: ''
  });
  const [file, setFile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleFileChange = (e) => {
    setFile(e.target.files[0]);
  };

  const handleSingleUpload = async (e) => {
    e.preventDefault();

    if (!formData.rawContent.trim()) {
      setError('Message content is required');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      await messagesApi.upload(formData);
      setSuccess('Message uploaded successfully');

      // Reset form
      setFormData({
        messageType: 'MT700',
        rawContent: '',
        senderId: '',
        receiverId: '',
        notes: ''
      });
    } catch (err) {
      setError('Failed to upload message');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleFileUpload = async (e) => {
    e.preventDefault();

    if (!file) {
      setError('Please select a file');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const response = await messagesApi.uploadFile(file);
      setSuccess(`Successfully uploaded ${response.data.length} messages`);
      setFile(null);
    } catch (err) {
      setError('Failed to upload file');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const loadSampleMessage = () => {
    setFormData(prev => ({
      ...prev,
      rawContent: `{1:F01BANKBEBBAXXX0000000000}{2:I700BANKUS33XXXXN}
:20:LC123456789
:31C:251231
:40A:IRREVOCABLE
:20:DOCUMENTARY CREDIT NUMBER
:31D:251231LOCATION
:50:APPLICANT NAME
APPLICANT ADDRESS
CITY, COUNTRY
:59:BENEFICIARY NAME
BENEFICIARY ADDRESS
CITY, COUNTRY
:32B:USD100000,00
:41A:AVAILABLE WITH ANY BANK
:42C:DRAFTS AT SIGHT
:43P:PARTIAL SHIPMENTS ALLOWED
:43T:TRANSSHIPMENT NOT ALLOWED
:44A:LOADING ON BOARD
:44E:PORT OF LOADING
:44F:PORT OF DISCHARGE
:44C:LATEST DATE OF SHIPMENT 251215
:45A:DESCRIPTION OF GOODS
COMMODITY: ELECTRONICS
QUANTITY: 1000 UNITS
:46A:DOCUMENTS REQUIRED
- COMMERCIAL INVOICE IN TRIPLICATE
- PACKING LIST
- BILL OF LADING
:47A:ADDITIONAL CONDITIONS
ALL DOCUMENTS MUST BE PRESENTED WITHIN 21 DAYS
:71B:CHARGES
ALL BANKING CHARGES OUTSIDE ISSUING BANK ARE FOR BENEFICIARY
:48:PERIOD FOR PRESENTATION 21 DAYS
:49:CONFIRMATION INSTRUCTIONS WITHOUT
:78:INSTRUCTIONS TO PAY/ACCEPT/NEGOTIATE BANK`,
      senderId: 'BANKBEBB',
      receiverId: 'BANKUS33'
    }));
    setSuccess('Sample MT700 message loaded');
  };

  return (
    <div className="container">
      <h2 className="page-title">Upload SWIFT Messages</h2>

      {error && <div className="error">{error}</div>}
      {success && <div className="success">{success}</div>}

      <div className="upload-mode-selector">
        <button
          className={`mode-button ${uploadMode === 'single' ? 'active' : ''}`}
          onClick={() => setUploadMode('single')}
        >
          Single Message
        </button>
        <button
          className={`mode-button ${uploadMode === 'file' ? 'active' : ''}`}
          onClick={() => setUploadMode('file')}
        >
          Upload File
        </button>
      </div>

      {uploadMode === 'single' ? (
        <div className="card">
          <form onSubmit={handleSingleUpload}>
            <div className="form-group">
              <label htmlFor="messageType">Message Type</label>
              <select
                id="messageType"
                name="messageType"
                className="input"
                value={formData.messageType}
                onChange={handleInputChange}
              >
                <option value="MT700">MT700 - Issue of Documentary Credit</option>
                <option value="MT710">MT710 - Advice of a Third Bank's DC</option>
                <option value="MT720">MT720 - Transfer of a DC</option>
                <option value="MT730">MT730 - Acknowledgement</option>
                <option value="MT740">MT740 - Authorization to Reimburse</option>
                <option value="MT750">MT750 - Discrepancy Notice</option>
              </select>
            </div>

            <div className="form-group">
              <label htmlFor="senderId">Sender ID</label>
              <input
                type="text"
                id="senderId"
                name="senderId"
                className="input"
                value={formData.senderId}
                onChange={handleInputChange}
                placeholder="e.g., BANKBEBB"
              />
            </div>

            <div className="form-group">
              <label htmlFor="receiverId">Receiver ID</label>
              <input
                type="text"
                id="receiverId"
                name="receiverId"
                className="input"
                value={formData.receiverId}
                onChange={handleInputChange}
                placeholder="e.g., BANKUS33"
              />
            </div>

            <div className="form-group">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <label htmlFor="rawContent">Message Content</label>
                <button
                  type="button"
                  className="button button-sm button-secondary"
                  onClick={loadSampleMessage}
                >
                  Load Sample
                </button>
              </div>
              <textarea
                id="rawContent"
                name="rawContent"
                className="textarea"
                value={formData.rawContent}
                onChange={handleInputChange}
                placeholder="Paste SWIFT message content here..."
                required
              />
            </div>

            <div className="form-group">
              <label htmlFor="notes">Notes (Optional)</label>
              <input
                type="text"
                id="notes"
                name="notes"
                className="input"
                value={formData.notes}
                onChange={handleInputChange}
                placeholder="Add any notes..."
              />
            </div>

            <button
              type="submit"
              className="button button-primary"
              disabled={loading}
            >
              {loading ? 'Uploading...' : 'Upload Message'}
            </button>
          </form>
        </div>
      ) : (
        <div className="card">
          <form onSubmit={handleFileUpload}>
            <div className="form-group">
              <label htmlFor="file">Select File</label>
              <div className="file-input-wrapper">
                <input
                  type="file"
                  id="file"
                  className="file-input"
                  onChange={handleFileChange}
                  accept=".txt,.swift"
                />
                {file && <p className="file-name">Selected: {file.name}</p>}
              </div>
              <p className="help-text">
                Upload a file containing multiple SWIFT messages. Each message should start with message type header.
              </p>
            </div>

            <button
              type="submit"
              className="button button-primary"
              disabled={loading}
            >
              {loading ? 'Uploading...' : 'Upload File'}
            </button>
          </form>
        </div>
      )}
    </div>
  );
}

export default Upload;
